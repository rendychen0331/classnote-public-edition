package com.rendy.classnote.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.CourseEntity
import com.rendy.classnote.databinding.FragmentCourseEditBinding

/**
 * 新增 / 編輯課程
 * navArgs: courseId（-1L = 新增）、dayOfWeek、period
 */
class CourseEditFragment : Fragment() {

    private var _binding: FragmentCourseEditBinding? = null
    private val binding get() = _binding!!

    private val args: CourseEditFragmentArgs by navArgs()

    // Navigation destination，直接持有自己的 ViewModel
    private val viewModel: ScheduleViewModel by viewModels {
        val repo = (requireActivity().application as ClassNoteApplication).courseRepository
        ScheduleViewModel.Factory(repo)
    }

    private val days = listOf("週一", "週二", "週三", "週四", "週五")
    private val periods = (1..7).map { "第 $it 節" }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCourseEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdowns()

        if (args.courseId > 0) {
            loadCourse(args.courseId)
        } else {
            setDropdown(binding.spinnerDay, days, args.dayOfWeek - 1)
            setDropdown(binding.spinnerPeriod, periods, args.period - 1)
        }

        binding.btnSave.setOnClickListener { saveCourse() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupDropdowns() {
        binding.spinnerDay.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, days)
        )
        binding.spinnerPeriod.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, periods)
        )
    }

    private fun setDropdown(view: android.widget.AutoCompleteTextView, items: List<String>, index: Int) {
        view.setText(items.getOrNull(index) ?: items[0], false)
    }

    private fun selectedIndex(view: android.widget.AutoCompleteTextView, items: List<String>): Int {
        val text = view.text.toString()
        return items.indexOf(text).coerceAtLeast(0)
    }

    private fun loadCourse(courseId: Long) {
        val course = viewModel.courses.value.firstOrNull { it.id == courseId } ?: return
        binding.etName.setText(course.name)
        binding.etTeacher.setText(course.teacher)
        binding.etRoom.setText(course.room)
        setDropdown(binding.spinnerDay, days, course.dayOfWeek - 1)
        setDropdown(binding.spinnerPeriod, periods, course.period - 1)
    }

    private fun saveCourse() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = "請輸入課程名稱"
            return
        }

        val course = CourseEntity(
            id = if (args.courseId > 0) args.courseId else 0,
            semesterId = viewModel.currentSemesterId.value,
            dayOfWeek = selectedIndex(binding.spinnerDay, days) + 1,
            period = selectedIndex(binding.spinnerPeriod, periods) + 1,
            name = name,
            teacher = binding.etTeacher.text.toString().trim(),
            room = binding.etRoom.text.toString().trim()
        )

        if (args.courseId > 0) viewModel.updateCourse(course)
        else viewModel.addCourse(course)

        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
