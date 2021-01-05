package com.sstudio.submission_made.core.data

import android.util.Log
import androidx.lifecycle.Transformations
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import com.sstudio.submission_made.core.data.source.local.LocalDataSource
import com.sstudio.submission_made.core.data.source.local.entity.FavoriteEntity
import com.sstudio.submission_made.core.data.source.local.entity.ScheduleEntity
import com.sstudio.submission_made.core.data.source.remote.RemoteDataSource
import com.sstudio.submission_made.core.data.source.remote.network.ApiResponse
import com.sstudio.submission_made.core.data.source.remote.response.ChannelResponse
import com.sstudio.submission_made.core.data.source.remote.response.ScheduleResponse
import com.sstudio.submission_made.core.domain.model.Channel
import com.sstudio.submission_made.core.domain.model.ChannelWithScheduleModel
import com.sstudio.submission_made.core.domain.model.Favorite
import com.sstudio.submission_made.core.domain.repository.ITvGuideRepository
import com.sstudio.submission_made.core.utils.AppExecutors
import com.sstudio.submission_made.core.utils.DataMapper
import com.sstudio.submission_made.vo.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TvGuideRepository private constructor(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val appExecutors: AppExecutors
) : ITvGuideRepository {

    companion object {
        @Volatile
        private var instance: ITvGuideRepository? = null

        fun getInstance(
            remoteData: RemoteDataSource,
            localData: LocalDataSource,
            appExecutors: AppExecutors
        ): ITvGuideRepository =
            instance ?: synchronized(this) {
                instance ?: TvGuideRepository(remoteData, localData, appExecutors)
            }
    }

    override fun getAllChannel(needFetch: Boolean): Flow<Resource<PagedList<Channel>>> {
        return object :
            NetworkBoundResource<PagedList<Channel>, ChannelResponse>() {
            override fun loadFromDB(): Flow<PagedList<Channel>> {

                val data = localDataSource.getAllChannels()
//                Log.d("mytag", "repository ${data.map { it }}")
                val groupItemFactory =
                    DataMapper.mapChannelEntitiesToDomain(data)

                val config = PagedList.Config.Builder()
                    .setEnablePlaceholders(false)
                    .setInitialLoadSizeHint(4)
                    .setPageSize(4)
                    .build()

                return LivePagedListBuilder(
                    groupItemFactory, config
                ).build().asFlow()
            }

            override fun shouldFetch(data: PagedList<Channel>?): Boolean =
                data == null || data.isEmpty() || needFetch

            override suspend fun createCall(): Flow<ApiResponse<ChannelResponse>> =
                remoteDataSource.getAllChannel()

            override suspend fun saveCallResult(data: ChannelResponse) {
                localDataSource.insertAllChannel(DataMapper.mapChanelResponsesToEntities(data.result))
            }
        }.asFlow()
    }

    override fun getSchedule(
        needFetch: Boolean,
        channelId: Int,
        date: String
    ): Flow<Resource<ChannelWithScheduleModel>> {
        return object : NetworkBoundResource<ChannelWithScheduleModel, ScheduleResponse>() {
            override fun loadFromDB(): Flow<ChannelWithScheduleModel> {

                return localDataSource.getChannelWithScheduleById(channelId, date).map {
                    DataMapper.mapChannelScheduleEntitiesToDomain(it)
                }
            }

            override fun shouldFetch(data: ChannelWithScheduleModel?): Boolean {
                Log.d("mytag", "repository data ${data}")
                return data?.schedule == null || needFetch
            }

            override suspend fun createCall(): Flow<ApiResponse<ScheduleResponse>> =
                remoteDataSource.getSchedules(channelId, date)

            override suspend fun saveCallResult(data: ScheduleResponse) {
                for (response in data.result) {
                    val showTimesSort = response.showTimes.sortedBy {
                        it.time
                    }
                    for (showTimes in showTimesSort) {
                        localDataSource.insertSchedule(
                            ScheduleEntity(
                                response.channelId,
                                response.date,
                                showTimes.time,
                                showTimes.title
                            )
                        )
                    }
                }
            }
        }.asFlow()
    }

    override fun getAllFavoriteChannel(): Flow<PagedList<Channel>> {
        val favoriteEntity =
            DataMapper.mapChannelFavoriteToDomainPagedList(localDataSource.getAllFavoriteChannel())

        val config = PagedList.Config.Builder()
            .setEnablePlaceholders(false)
            .setInitialLoadSizeHint(4)
            .setPageSize(4)
            .build()
        return flow {
            LivePagedListBuilder(
                favoriteEntity, config
            ).build().map { it }
        }
    }

    override fun setFavorite(channelId: Int) {
        appExecutors.diskIO().execute { localDataSource.insertFavorite(FavoriteEntity(channelId)) }
    }

    override fun getFavoriteById(channelId: Int): Flow<Favorite?> =
        localDataSource.getFavoriteById(channelId)?.let {
            it.map { favoriteEntity ->
                Favorite(favoriteEntity?.channelId)
            }
        }

    override fun deleteFavorite(channelId: Int) {
        appExecutors.diskIO().execute { localDataSource.deleteFavoriteTv(channelId) }
    }
}

