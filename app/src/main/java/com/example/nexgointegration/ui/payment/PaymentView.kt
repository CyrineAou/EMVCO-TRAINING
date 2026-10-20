package com.example.nexgointegration.ui.payment

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.nexgointegration.payment.PaymentUiState

/**
 * Écran de paiement.
 * Il reçoit un [PaymentUiState] via [render] et le transforme en image.
 * Il ne sait rien du PaymentManager : il remonte seulement les clics.
 */
class PaymentView(
    context: Context,
    private val amountLabel: String
) : LinearLayout(context) {

    var onCancelClicked: () -> Unit = {}
    var onRetryClicked: () -> Unit = {}

    /** Ce qu'il faut afficher pour un état donné. */
    private data class Screen(
        val illustration: StatusIllustrationView.Mode,
        val title: String,
        val subtitle: String,
        val background: Int,
        val actionLabel: String? = null,
        val action: () -> Unit = {}
    )

    private val amountView = label(size = 44f, bold = true, color = PaymentTheme.TEXT)
    private val illustration = StatusIllustrationView(context)
    private val titleView = label(size = 26f, bold = true, color = PaymentTheme.TEXT)
    private val subtitleView = label(size = 16f, bold = false, color = PaymentTheme.TEXT_SOFT)
    private val actionButton = buildActionButton()

    private var currentBackground = PaymentTheme.BG_WAITING

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dpInt(24f), dpInt(24f), dpInt(24f), dpInt(24f))
        setBackgroundColor(currentBackground)

        amountView.text = amountLabel

        addView(amountView)
        addView(illustration, LayoutParams(dpInt(240f), dpInt(240f)).apply {
            topMargin = dpInt(32f); bottomMargin = dpInt(32f)
        })
        addView(titleView)
        addView(subtitleView, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dpInt(8f) })
        addView(actionButton, LayoutParams(MATCH_PARENT, dpInt(52f)).apply { topMargin = dpInt(32f) })

        render(PaymentUiState.Preparing)
    }

    fun render(state: PaymentUiState) {
        val screen = state.toScreen()

        illustration.show(screen.illustration)
        titleView.text = screen.title
        subtitleView.text = screen.subtitle
        fadeBackgroundTo(screen.background)

        actionButton.visibility = if (screen.actionLabel == null) INVISIBLE else VISIBLE
        actionButton.text = screen.actionLabel
        actionButton.setOnClickListener { screen.action() }
    }

    // ---------------------------------------------------------
    // État -> écran
    // ---------------------------------------------------------

    private fun PaymentUiState.toScreen(): Screen = when (this) {
        PaymentUiState.Preparing -> Screen(
            StatusIllustrationView.Mode.PROCESSING,
            "Préparation…", "Le terminal s'initialise",
            PaymentTheme.BG_WAITING
        )

        PaymentUiState.WaitingForCard -> Screen(
            StatusIllustrationView.Mode.TAP,
            "Approchez votre carte", "Posez-la sur le lecteur sans contact",
            PaymentTheme.BG_WAITING,
            "Annuler", onCancelClicked
        )

        PaymentUiState.Processing -> Screen(
            StatusIllustrationView.Mode.PROCESSING,
            "Paiement en cours", "Ne retirez pas votre carte",
            PaymentTheme.BG_PROCESSING
        )

        PaymentUiState.Success -> Screen(
            StatusIllustrationView.Mode.SUCCESS,
            "Paiement accepté", "Merci ! Vous pouvez retirer votre carte",
            PaymentTheme.BG_SUCCESS,
            "Nouveau paiement", onRetryClicked
        )

        is PaymentUiState.Failure -> Screen(
            StatusIllustrationView.Mode.FAILURE,
            "Paiement refusé", reason,
            PaymentTheme.BG_FAILURE,
            "Réessayer", onRetryClicked
        )

        PaymentUiState.Cancelled -> Screen(
            StatusIllustrationView.Mode.FAILURE,
            "Paiement annulé", "Aucun montant n'a été débité",
            PaymentTheme.BG_FAILURE,
            "Recommencer", onRetryClicked
        )
    }

    // ---------------------------------------------------------
    // Fabrication des composants
    // ---------------------------------------------------------

    private fun fadeBackgroundTo(target: Int) {
        if (target == currentBackground) return
        ValueAnimator.ofObject(ArgbEvaluator(), currentBackground, target).apply {
            duration = PaymentTheme.ANIMATION_BG_MS
            addUpdateListener { setBackgroundColor(it.animatedValue as Int) }
            start()
        }
        currentBackground = target
    }

    private fun label(size: Float, bold: Boolean, color: Int) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun buildActionButton() = Button(context).apply {
        isAllCaps = false
        textSize = 17f
        setTextColor(PaymentTheme.BG_WAITING)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(26f)
        }
    }
}