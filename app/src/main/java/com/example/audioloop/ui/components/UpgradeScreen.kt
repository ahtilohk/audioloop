package com.example.audioloop.ui.components

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.example.audioloop.AppIcons
import com.example.audioloop.R
import com.example.audioloop.ui.theme.*

/**
 * Premium bottom sheet that explains Pro benefits and handles purchases.
 * Designed with a rich, inviting aesthetic (glassmorphism/gradients).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    products: List<ProductDetails>,
    onPurchase: (Activity, ProductDetails) -> Unit,
    themeColors: AppColorPalette,
    isPro: Boolean
) {
    if (!isVisible) return

    val context = LocalContext.current
    val activity = context as? Activity
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Zinc950,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Zinc700) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.btn_cancel), tint = Zinc600)
                }
            }

            // ── Premium Header ──
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(themeColors.primary, themeColors.primary400)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Loop, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.upgrade_pro_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )

            Text(
                text = stringResource(R.string.upgrade_pro_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = Zinc400,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(32.dp))

            // ── Features List ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Zinc900.copy(alpha = 0.4f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProFeatureRow(AppIcons.Check, stringResource(R.string.upgrade_feature_unlimited), themeColors)
                ProFeatureRow(AppIcons.Check, stringResource(R.string.upgrade_feature_cloud), themeColors)
                ProFeatureRow(AppIcons.Check, stringResource(R.string.upgrade_feature_processing), themeColors)
                ProFeatureRow(AppIcons.Check, stringResource(R.string.upgrade_feature_coach), themeColors)
            }

            Spacer(Modifier.height(40.dp))

            if (isPro) {
                // ── Already Pro State ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(themeColors.primary.copy(alpha = 0.1f))
                        .border(1.dp, themeColors.primary, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Check, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.upgrade_success),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // ── Pricing Options ──
                if (products.isEmpty()) {
                    CircularProgressIndicator(color = themeColors.primary, modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.msg_loading_offers), color = Zinc500, modifier = Modifier.padding(top = 16.dp), fontSize = 12.sp)
                } else {
                    for (product in products.sortedBy { it.productId }) {
                        val price = product.subscriptionOfferDetails?.getOrNull(0)?.pricingPhases?.pricingPhaseList?.getOrNull(0)?.formattedPrice
                            ?: product.oneTimePurchaseOfferDetails?.formattedPrice
                            ?: "---"
                        
                        val period = when {
                            product.productId.contains("monthly") -> stringResource(R.string.label_per_month)
                            product.productId.contains("yearly") -> stringResource(R.string.label_per_year)
                            else -> ""
                        }
                        val isYearly = product.productId.contains("yearly")
                        
                        Surface(
                            onClick = { activity?.let { onPurchase(it, product) } },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isYearly) themeColors.primary.copy(alpha = 0.05f) else Zinc900,
                            border = BorderStroke(
                                1.5.dp, 
                                if (isYearly) themeColors.primary.copy(alpha = 0.5f) else Zinc800
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Box {
                                if (isYearly) {
                                    Surface(
                                        color = themeColors.primary,
                                        shape = RoundedCornerShape(bottomStart = 12.dp),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            stringResource(R.string.label_best_value),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            product.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )
                                        Text(
                                            product.description,
                                            color = Zinc500,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = price,
                                            color = if (isYearly) Color.White else themeColors.primary200,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp
                                        )
                                        if (period.isNotEmpty()) {
                                            Text(period, color = Zinc500, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = { /* Restore Purchases Logic could go here */ }) {
                    Text(stringResource(R.string.btn_restore_purchase), color = Zinc500, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(icon: ImageVector, label: String, themeColors: AppColorPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Zinc200, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
