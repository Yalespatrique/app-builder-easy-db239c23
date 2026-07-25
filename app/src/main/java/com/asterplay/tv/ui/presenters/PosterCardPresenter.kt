package com.asterplay.tv.ui.presenters

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil.load
import com.asterplay.tv.R
import com.asterplay.tv.data.Channel

/**
 * Card estilo pôster Roku/XCIPTV — 220x330dp, logo do canal com fallback,
 * título embaixo e descrição opcional (grupo). Usa Coil pra imagens.
 */
class PosterCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(WIDTH_DP, HEIGHT_DP)
            infoAreaBackgroundColor = ContextCompat.getColor(context, R.color.aster_bg_alt)
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(vh: ViewHolder, item: Any?) {
        val ch = item as? Channel ?: return
        val card = vh.view as ImageCardView
        card.titleText = ch.name
        card.contentText = ch.group.ifBlank { "Asterplay" }
        card.mainImageView.load(ch.logo.ifBlank { null }) {
            placeholder(R.drawable.banner)
            error(R.drawable.banner)
            crossfade(true)
        }
    }

    override fun onUnbindViewHolder(vh: ViewHolder) {
        val card = vh.view as ImageCardView
        card.mainImage = null
        card.badgeImage = null
    }

    companion object {
        private const val WIDTH_DP = 260
        private const val HEIGHT_DP = 380
    }
}
