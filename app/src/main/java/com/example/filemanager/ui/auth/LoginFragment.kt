package com.example.filemanager.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.filemanager.R
import com.example.filemanager.databinding.FragmentLoginBinding
import com.example.filemanager.utils.applySystemBarPadding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)
        binding.root.applySystemBarPadding(alsoBottom = true)
        if (viewModel.isLoggedIn()) {
            findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
            return
        }

        binding.btnLogin.setOnClickListener {
            viewModel.login(
                email = binding.etEmail.text.toString(),
                password = binding.etPassword.text.toString()
            )
        }
        binding.tvCreateAccount.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signUpFragment)
        }

        viewModel.authSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
            } else {
                Toast.makeText(requireContext(), R.string.login_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
