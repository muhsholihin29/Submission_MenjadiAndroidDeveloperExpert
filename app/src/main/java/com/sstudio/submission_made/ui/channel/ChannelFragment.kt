package com.sstudio.submission_made.ui.channel

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.sstudio.submission_made.R
import com.sstudio.submission_made.core.ui.ViewModelFactory
import com.sstudio.submission_made.ui.schedule.ScheduleActivity
import com.sstudio.submission_made.vo.Status
import kotlinx.android.synthetic.main.fragment_channel.*

class ChannelFragment : Fragment() {

    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var viewModel: ChannelViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_channel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (activity != null) {
            val factory = ViewModelFactory.getInstance(requireActivity())
            viewModel = ViewModelProvider(this, factory)[ChannelViewModel::class.java]
            channelAdapter = ChannelAdapter()
            observeData()

            swipe_layout.setOnRefreshListener {
                viewModel.fetchListMovie()
                observeData()
                swipe_layout.isRefreshing = false
            }
            with(rv_list_favorite) {
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(true)
//                adapter = movieAdapter
            }
        }

        channelAdapter.onItemClick = { selectedData ->
            val intent = Intent(activity, ScheduleActivity::class.java)
            intent.putExtra(ScheduleActivity.EXTRA_SCHEDULE, selectedData)
            startActivity(intent)
        }
    }

    private fun observeData() {
        viewModel.listChannel?.observe(this, { listMovie ->
            if (listMovie != null) {
                when (listMovie.status) {
                    Status.LOADING -> progress_bar.visibility = View.VISIBLE
                    Status.SUCCESS -> {
                        progress_bar.visibility = View.GONE
                        channelAdapter.submitList(listMovie.data)
                        rv_list_favorite.adapter = channelAdapter //why??
                    }
                    Status.ERROR -> {
                        progress_bar.visibility = View.GONE
                        Toast.makeText(context, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}