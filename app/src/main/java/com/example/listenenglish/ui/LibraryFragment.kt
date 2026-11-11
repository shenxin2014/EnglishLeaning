package com.example.listenenglish.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.listenenglish.databinding.FragmentLibraryBinding

/** 展示本地课程、转写状态和空库提示，点选课程进入详情。 */
class LibraryFragment : Fragment() {
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by activityViewModels()
    private val adapter = LessonAdapter { lesson -> (activity as? MainActivity)?.openLesson(lesson.id) }

    /** 加载课程库布局并建立视图绑定。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 配置课程列表，并随 Room 数据切换列表和空状态。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.lessonList.layoutManager = LinearLayoutManager(requireContext())
        binding.lessonList.adapter = adapter
        viewModel.lessons.observe(viewLifecycleOwner) { lessons ->
            adapter.submitList(lessons)
            binding.emptyState.visibility = if (lessons.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** 释放绑定，避免保留已销毁的课程库视图。 */
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
