package com.avnikahraman.safedose.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.avnikahraman.safedose.databinding.ItemMedicineBinding
import com.avnikahraman.safedose.models.Medicine
import java.text.SimpleDateFormat
import java.util.*
import com.bumptech.glide.Glide
import com.avnikahraman.safedose.R

/**
 * İlaç listesi için RecyclerView Adapter
 */
class MedicineAdapter(
    private val onItemClick: (Medicine) -> Unit
) : RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder>() {

    private val medicines = mutableListOf<Medicine>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("tr"))

    /**
     * Listeyi güncelle
     */
    fun submitList(newMedicines: List<Medicine>) {
        medicines.clear()
        medicines.addAll(newMedicines)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val binding = ItemMedicineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MedicineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        holder.bind(medicines[position])
    }

    override fun getItemCount(): Int = medicines.size

    /**
     * ViewHolder
     */
    inner class MedicineViewHolder(
        private val binding: ItemMedicineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(medicine: Medicine) {
            binding.apply {
                // İlaç adı ve dozaj
                tvMedicineName.text = medicine.name
                tvDosage.text = medicine.dosage
                if (medicine.imageUrl.isNotEmpty()) {
                    Glide.with(itemView.context)
                        .load(medicine.imageUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(ivMedicineImage)
                } else {
                    ivMedicineImage.setImageResource(R.drawable.ic_launcher_foreground)
                }

                // Kullanım bilgisi
                val usageInfo = "Günde ${medicine.timesPerDay}x, ${medicine.intervalHours} saat aralıkla"
                tvUsageInfo.text = usageInfo

                // Başlangıç saati
                tvStartTime.text = "İlk doz: ${medicine.startTime}"

                // Süre bilgisi
                val daysRemaining = calculateDaysRemaining(medicine)
                if (daysRemaining > 0) {
                    tvDaysRemaining.text = "$daysRemaining gün kaldı"
                    // Aktif - yeşil arka plan
                    cardMedicine.setCardBackgroundColor(
                        itemView.context.getColor(android.R.color.holo_green_light)
                    )
                } else {
                    tvDaysRemaining.text = "Tamamlandı"
                    // Tamamlanmış - gri arka plan
                    cardMedicine.setCardBackgroundColor(
                        itemView.context.getColor(android.R.color.darker_gray)
                    )
                }

                // Başlangıç tarihi
                val startDateText = "Başlangıç: ${dateFormat.format(Date(medicine.startDate))}"
                tvStartDate.text = startDateText

                // Click listener
                root.setOnClickListener {
                    onItemClick(medicine)
                }
            }
        }

        /**
         * Kalan gün sayısını hesapla
         */
        private fun calculateDaysRemaining(medicine: Medicine): Int {
            val endDate = medicine.getEndDate()
            val currentTime = System.currentTimeMillis()
            val remainingMillis = endDate - currentTime
            val remainingDays = (remainingMillis / (1000 * 60 * 60 * 24)).toInt()
            return maxOf(0, remainingDays)
        }
    }
}