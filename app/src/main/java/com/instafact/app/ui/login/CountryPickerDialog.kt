package com.instafact.app.ui.login

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.R
import com.instafact.app.databinding.DialogCountryPickerBinding
import com.instafact.app.databinding.ItemCountryBinding
import com.instafact.app.utils.Countries
import com.instafact.app.utils.Country

object CountryPickerDialog {

    fun show(
        activity: Activity,
        selected: Country,
        onSelected: (Country) -> Unit,
    ) {
        val binding = DialogCountryPickerBinding.inflate(LayoutInflater.from(activity))

        val dialog = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Instafact_Dialog)
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .create()

        val adapter = CountryAdapter(selected) { country ->
            onSelected(country)
            dialog.dismiss()
        }

        binding.countryRecyclerView.layoutManager = LinearLayoutManager(activity)
        binding.countryRecyclerView.adapter = adapter
        adapter.submit(Countries.ALL)

        binding.countrySearchEditText.doAfterTextChanged { editable ->
            val results = Countries.search(editable?.toString().orEmpty())
            adapter.submit(results)
            binding.countryEmptyTextView.isVisible = results.isEmpty()
            binding.countryRecyclerView.isVisible = results.isNotEmpty()
        }

        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(activity, R.drawable.bg_dialog_surface),
        )
        dialog.show()

        // Open on the current selection so it is obvious what is active.
        val index = Countries.ALL.indexOfFirst { it.isoCode == selected.isoCode }
        if (index > 0) {
            binding.countryRecyclerView.scrollToPosition(index)
        }
    }

    private class CountryAdapter(
        private val selected: Country,
        private val onClick: (Country) -> Unit,
    ) : RecyclerView.Adapter<CountryAdapter.CountryViewHolder>() {

        private val items = mutableListOf<Country>()

        fun submit(countries: List<Country>) {
            items.clear()
            items.addAll(countries)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryViewHolder {
            val binding = ItemCountryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return CountryViewHolder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: CountryViewHolder, position: Int) {
            holder.bind(items[position])
        }

        inner class CountryViewHolder(
            private val binding: ItemCountryBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(country: Country) {
                val context = binding.root.context
                binding.countryFlagTextView.text = country.flag
                binding.countryNameTextView.text = country.name
                binding.countryDialCodeTextView.text =
                    context.getString(R.string.country_dial_code_format, country.dialCode)

                val isSelected = country.isoCode == selected.isoCode
                binding.countryNameTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isSelected) R.color.brand_primary else R.color.brand_text,
                    ),
                )
                binding.root.setOnClickListener { onClick(country) }
            }
        }
    }
}
