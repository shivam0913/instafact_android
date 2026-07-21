package com.instafact.app.ui.support

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.instafact.app.R
import com.instafact.app.databinding.ActivityHelpSupportBinding
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars

class HelpSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHelpSupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHelpSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureSystemBars(
            statusBarColorRes = R.color.brand_surface,
            navigationBarColorRes = R.color.brand_background,
            lightStatusBar = true,
        )
        binding.rootLayout.applySystemBarInsets(applyTop = true, applyBottom = true)

        binding.backButton.setOnClickListener { finish() }
        binding.faqRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.faqRecyclerView.adapter = FaqAdapter(buildFaqs())
        binding.writeToUsButton.setOnClickListener { openEmailComposer() }
    }

    private fun buildFaqs(): List<FaqItem> = listOf(
        FaqItem(
            question = getString(R.string.faq_question_1),
            answer = getString(R.string.faq_answer_1),
        ),
        FaqItem(
            question = getString(R.string.faq_question_2),
            answer = getString(R.string.faq_answer_2),
        ),
        FaqItem(
            question = getString(R.string.faq_question_3),
            answer = getString(R.string.faq_answer_3),
        ),
        FaqItem(
            question = getString(R.string.faq_question_4),
            answer = getString(R.string.faq_answer_4),
        ),
        FaqItem(
            question = getString(R.string.faq_question_5),
            answer = getString(R.string.faq_answer_5),
        ),
        FaqItem(
            question = getString(R.string.faq_question_6),
            answer = getString(R.string.faq_answer_6),
        ),
    )

    private fun openEmailComposer() {
        val emailIntent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:connect@instafact.co"),
        ).apply {
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.write_to_us_subject))
        }
        runCatching {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.write_to_us)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
        }
    }
}
