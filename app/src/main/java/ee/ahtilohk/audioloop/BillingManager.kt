package ee.ahtilohk.audioloop

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Play Billing for the Pro version.
 * Handles connections, product queries, and purchase flows.
 */
@Singleton
class BillingManager @Inject constructor(
    private val context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    private val TAG = "BillingManager"

    // List of product IDs defined in Play Console
    private val productIds = listOf(
        "pro_monthly",
        "pro_yearly",
        "pro_lifetime"
    )

    private val _isProUser = MutableStateFlow(false)
    val isProUser = _isProUser.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products = _products.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected = _isServiceConnected.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        Log.d(TAG, "Starting billing connection...")
        billingClient.startConnection(object : BillingClientStateListener {
            private var retryCount = 0
            private val maxRetries = 5

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected.")
                _isServiceConnected.value = false
                
                if (retryCount < maxRetries) {
                    val delayMillis = Math.pow(2.0, retryCount.toDouble()).toLong() * 1000
                    Log.d(TAG, "Retrying billing connection in ${delayMillis}ms (attempt ${retryCount + 1})...")
                    scope.launch {
                        delay(delayMillis)
                        startConnection()
                    }
                    retryCount++
                }
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing setup successful.")
                    _isServiceConnected.value = true
                    retryCount = 0 // Reset on success
                    queryPurchases()
                    queryProductDetails()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _isServiceConnected.value = false
                }
            }
        })
    }

    /** Query all available products from Play Store. */
    private fun queryProductDetails() {
        val subsList = productIds.filter { it.contains("monthly") || it.contains("yearly") }.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val inAppList = productIds.filter { it.contains("lifetime") }.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val combinedList = subsList + inAppList
        if (combinedList.isEmpty()) return

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(combinedList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Found ${productDetailsList.size} products.")
                _products.value = productDetailsList
            } else {
                Log.e(TAG, "Failed to query products: ${billingResult.debugMessage}")
            }
        }
    }

    /** Refresh current purchase status (Pro vs Free). */
    fun queryPurchases() {
        // Query Subscriptions
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }

        // Query In-App (One-time)
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            _isProUser.value = true
        }

        // Acknowledge if not already
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }.forEach {
            acknowledgePurchase(it)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged.")
                _isProUser.value = true
            }
        }
    }

    /** Starts the checkout process for a specific product. */
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        // If it's a subscription, we might need an offer token.
                        // For simplicity picking the first base plan.
                        .apply {
                            productDetails.subscriptionOfferDetails?.getOrNull(0)?.let {
                                setOfferToken(it.offerToken)
                            }
                        }
                        .build()
                )
            )
            .build()

        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled purchase.")
        } else {
            Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
        }
    }
}
