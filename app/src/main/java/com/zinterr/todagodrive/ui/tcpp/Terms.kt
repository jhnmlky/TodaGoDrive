package com.zinterr.todagodrive.ui.tcpp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import androidx.navigation.fragment.findNavController
import com.zinterr.todagodrive.databinding.TermsBinding

class Terms : Fragment() {

    private lateinit var binding: TermsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = TermsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}