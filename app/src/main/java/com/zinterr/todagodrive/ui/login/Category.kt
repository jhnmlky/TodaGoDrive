package com.zinterr.todagodrive.ui.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import androidx.navigation.fragment.findNavController
import com.zinterr.todagodrive.databinding.CategoryBinding

class Category : Fragment() {

    private lateinit var binding: CategoryBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.driver.setOnClickListener {
            findNavController().navigate(CategoryDirections.actionCategoryToSignupDriver())
        }

        binding.association.setOnClickListener {
            findNavController().navigate(CategoryDirections.actionCategoryToSignupAssociation())
        }
    }
}