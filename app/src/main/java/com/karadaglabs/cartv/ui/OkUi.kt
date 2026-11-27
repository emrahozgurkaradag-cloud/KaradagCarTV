package com.karadaglabs.cartv.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karadaglabs.cartv.CarScreen
import com.karadaglabs.cartv.ChannelLogos
import com.karadaglabs.cartv.IptvChannel
import com.karadaglabs.cartv.R
import com.karadaglabs.cartv.ui.theme.OkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ÖK tasarım sistemi: telefon ve araç ekranı aynı bileşenleri kullanır,
 * ölçüler [OkMetrics] ile ekran sınıfına göre değişir.
 *  - Araç ekranı (UI_MODE_TYPE_CAR / Automotive) veya kısa yatay ekran
 *    (head-unit benzeri, yükseklik < 480dp): daha büyük dokunma hedefi
 *    (≥ 76dp), daha büyük yazı, daha az sütun.
 */
@Immutable
data class OkMetrics(
    val car: Boolean,
    val compactHeight: Boolean,
    val gutter: Dp,
    val touch: Dp,
    val tileHeight: Dp,
    val cardMinWidth: Dp,
    val logo: Dp,
    val title: TextUnit,
    val body: TextUnit
)

@Composable
fun rememberOkMetrics(): OkMetrics {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val car = remember(config) { CarScreen.isCarScreen(context) }
    val compact = config.screenHeightDp < 480
    return remember(car, compact, config.screenWidthDp) {
        when {
            car -> OkMetrics(car, compact, 20.dp, 76.dp, if (compact) 112.dp else 140.dp, 260.dp, 64.dp, 22.sp, 18.sp)
            compact -> OkMetrics(car, true, 16.dp, 64.dp, 104.dp, 220.dp, 52.dp, 18.sp, 15.sp)
            else -> OkMetrics(car, false, 20.dp, 64.dp, 128.dp, 240.dp, 56.dp, 20.sp, 16.sp)
        }
    }
}

@Composable
fun OkLogoMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(OkColors.AnthraciteLight)
            .border(1.dp, OkColors.Outline, RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ok_monogram),
            contentDescription = "ÖK",
            modifier = Modifier.size(size * 1.05f)
        )
    }
}

@Composable
fun OkWordmark(size: TextUnit) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = OkColors.Ink, fontWeight = FontWeight.Bold)) { append("Karadag") }
            withStyle(SpanStyle(color = OkColors.Orange, fontWeight = FontWeight.Bold)) { append("CarTV") }
        },
        fontSize = size,
        maxLines = 1
    )
}

/** Üst bar: ÖK + KaradagCarTV, isteğe bağlı geri ve sağ eylemler. */
@Composable
fun OkTopBar(
    metrics: OkMetrics,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            OkIconButton(R.drawable.ic_ok_back, "Geri", metrics, onBack)
            Spacer(Modifier.width(8.dp))
        }
        OkLogoMark(if (metrics.car) 52.dp else 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            OkWordmark(if (metrics.car) 24.sp else 21.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = OkColors.InkMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
fun OkIconButton(
    @DrawableRes icon: Int,
    label: String,
    metrics: OkMetrics,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
    tint: Color = OkColors.Ink
) {
    Box(
        Modifier
            .size(metrics.touch)
            .clip(CircleShape)
            .background(OkColors.Surface)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(26.dp), color = OkColors.Orange, strokeWidth = 3.dp)
        } else {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = if (enabled) tint else OkColors.Outline,
                modifier = Modifier.size(if (metrics.car) 32.dp else 28.dp)
            )
        }
    }
}

@Composable
fun OkSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    metrics: OkMetrics,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = OkColors.InkMuted, fontSize = metrics.body) },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_ok_search), null, tint = OkColors.Orange)
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                Text(
                    "Temizle",
                    color = OkColors.InkMuted,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onValueChange("") }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = metrics.body, color = OkColors.Ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(metrics.touch / 2),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OkColors.Orange,
            unfocusedBorderColor = OkColors.Outline,
            focusedContainerColor = OkColors.Surface,
            unfocusedContainerColor = OkColors.Surface,
            cursorColor = OkColors.Orange
        ),
        modifier = modifier.heightIn(min = metrics.touch)
    )
}

/** Ana ekran büyük kutucuğu (Canlı TV / YouTube / Radyo / Google). */
@Composable
fun OkFeatureTile(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    metrics: OkMetrics,
    enabled: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (accent && enabled) OkColors.Orange else OkColors.Surface,
        border = if (accent && enabled) null else BorderStroke(1.dp, OkColors.Outline),
        modifier = modifier
            .height(metrics.tileHeight)
            .semantics { contentDescription = "$title. $subtitle" }
    ) {
        val fg = when {
            !enabled -> OkColors.InkMuted
            accent -> OkColors.Anthracite
            else -> OkColors.Ink
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier
                    .size(if (metrics.car) 52.dp else 46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (accent && enabled) OkColors.Anthracite.copy(alpha = 0.14f) else OkColors.OrangeSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (enabled) icon else R.drawable.ic_ok_lock),
                    contentDescription = null,
                    tint = if (accent && enabled) OkColors.Anthracite else OkColors.Orange,
                    modifier = Modifier.size(if (metrics.car) 30.dp else 26.dp)
                )
            }
            Column {
                Text(title, color = fg, fontSize = metrics.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    subtitle,
                    color = if (accent && enabled) OkColors.Anthracite.copy(alpha = 0.75f) else OkColors.InkMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun OkChip(
    text: String,
    selected: Boolean,
    metrics: OkMetrics,
    onClick: () -> Unit,
    count: Int? = null,
    @DrawableRes icon: Int? = null
) {
    val shape = RoundedCornerShape(metrics.touch / 2)
    Row(
        Modifier
            .heightIn(min = if (metrics.car) 60.dp else 48.dp)
            .clip(shape)
            .background(if (selected) OkColors.Orange else OkColors.Surface)
            .border(1.dp, if (selected) OkColors.Orange else OkColors.Outline, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val fg = if (selected) OkColors.Anthracite else OkColors.Ink
        if (icon != null) Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, color = fg, fontSize = metrics.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count != null) {
            Text("$count", color = if (selected) OkColors.Anthracite.copy(alpha = 0.7f) else OkColors.InkMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun OkChannelLogo(channel: IptvChannel, size: Dp) {
    val bitmap by produceState(ChannelLogos.cached(channel.logoUrl), channel.logoUrl) {
        if (value == null && !channel.logoUrl.isNullOrBlank()) {
            value = withContext(Dispatchers.IO) { ChannelLogos.load(channel.logoUrl) }
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(if (bitmap != null) Color.White else OkColors.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        } else {
            Text(
                ChannelLogos.initials(channel.name),
                color = OkColors.Orange,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.32f).sp
            )
        }
    }
}

/** Kanal kartı: logo, ad, kategori, CANLI rozeti ve favori yıldızı. */
@Composable
fun OkChannelCard(
    channel: IptvChannel,
    categoryLabel: String?,
    favorite: Boolean,
    metrics: OkMetrics,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        color = OkColors.Surface,
        border = BorderStroke(1.dp, if (favorite) OkColors.OrangeDim else OkColors.Outline),
        modifier = modifier
            .heightIn(min = metrics.touch + 20.dp)
            .semantics { contentDescription = "${channel.name} kanalını aç" }
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OkChannelLogo(channel, metrics.logo)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = OkColors.Ink,
                    fontSize = metrics.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(OkColors.LiveRed))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOfNotNull("CANLI", categoryLabel).joinToString(" · "),
                        color = OkColors.InkMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                Modifier
                    .size(metrics.touch)
                    .clip(CircleShape)
                    .clickable(role = Role.Checkbox, onClick = onToggleFavorite)
                    .semantics { contentDescription = if (favorite) "Favorilerden çıkar" else "Favorilere ekle" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (favorite) R.drawable.ic_ok_star else R.drawable.ic_ok_star_outline),
                    contentDescription = null,
                    tint = if (favorite) OkColors.Orange else OkColors.InkMuted,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/** Yatay kaydırılan küçük kanal kartı (öne çıkan / son izlenen rafları). */
@Composable
fun OkChannelPoster(channel: IptvChannel, metrics: OkMetrics, badge: String?, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        color = OkColors.Surface,
        border = BorderStroke(1.dp, OkColors.Outline),
        modifier = Modifier
            .width(if (metrics.car) 188.dp else 164.dp)
            .semantics { contentDescription = "${channel.name} kanalını aç" }
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OkChannelLogo(channel, if (metrics.car) 84.dp else 72.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                channel.name,
                color = OkColors.Ink,
                fontSize = metrics.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge != null) {
                Text(badge, color = OkColors.Orange, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun OkSectionTitle(text: String, metrics: OkMetrics, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(OkColors.Orange))
        Spacer(Modifier.width(10.dp))
        Text(text, color = OkColors.Ink, fontSize = metrics.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = OkColors.InkMuted, fontSize = 13.sp)
    }
}

/** Yükleniyor / boş / bağlantı hatası / oynatma hatası durumu. */
@Composable
fun OkStateMessage(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    metrics: OkMetrics,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(metrics.gutter * 2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(OkColors.OrangeSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, tint = OkColors.Orange, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = OkColors.Ink, fontSize = metrics.title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = OkColors.InkMuted, fontSize = metrics.body, modifier = Modifier.widthIn(max = 520.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            OkPrimaryButton(actionLabel, metrics, onAction)
        }
    }
}

@Composable
fun OkPrimaryButton(text: String, metrics: OkMetrics, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(metrics.touch / 2),
        color = OkColors.Orange,
        modifier = Modifier.heightIn(min = metrics.touch)
    ) {
        Box(Modifier.padding(horizontal = 28.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, color = OkColors.Anthracite, fontSize = metrics.body, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OkLoading(message: String, metrics: OkMetrics) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(metrics.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OkLogoMark(84.dp)
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(color = OkColors.Orange)
        Spacer(Modifier.height(14.dp))
        Text(message, color = OkColors.InkMuted, fontSize = metrics.body)
    }
}

/** İnce bilgi şeridi (çevrimdışı önbellek, uyarı). */
@Composable
fun OkBanner(@DrawableRes icon: Int, text: String, metrics: OkMetrics, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OkColors.OrangeSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = OkColors.Orange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = OkColors.Ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
