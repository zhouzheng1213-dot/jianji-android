package com.jianji.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyYen
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 图标注册表：数据库里只存 key，这里做 key -> ImageVector 的映射。
 * 新增图标只需在 [map] 里加一行，历史数据不会因此失效。
 */
object AppIcons {

    private val map: Map<String, ImageVector> = mapOf(
        // 支出
        "restaurant" to Icons.Filled.Restaurant,
        "fastfood" to Icons.Filled.Fastfood,
        "cafe" to Icons.Filled.LocalCafe,
        "pizza" to Icons.Filled.LocalPizza,
        "cart" to Icons.Filled.ShoppingCart,
        "shopping" to Icons.Filled.ShoppingBag,
        "store" to Icons.Filled.Storefront,
        "kitchen" to Icons.Filled.Kitchen,
        "bus" to Icons.Filled.DirectionsBus,
        "car" to Icons.Filled.DirectionsCar,
        "train" to Icons.Filled.Train,
        "bike" to Icons.Filled.DirectionsBike,
        "flight" to Icons.Filled.Flight,
        "gas" to Icons.Filled.LocalGasStation,
        "home" to Icons.Filled.Home,
        "wifi" to Icons.Filled.Wifi,
        "laundry" to Icons.Filled.LocalLaundryService,
        "phone" to Icons.Filled.PhoneAndroid,
        "game" to Icons.Filled.SportsEsports,
        "tv" to Icons.Filled.Tv,
        "bar" to Icons.Filled.LocalBar,
        "cake" to Icons.Filled.Cake,
        "sport" to Icons.Filled.FitnessCenter,
        "soccer" to Icons.Filled.SportsSoccer,
        "pet" to Icons.Filled.Pets,
        "baby" to Icons.Filled.ChildCare,
        "spa" to Icons.Filled.Spa,
        "beauty" to Icons.Filled.Face,
        "clothes" to Icons.Filled.Checkroom,
        "pharmacy" to Icons.Filled.LocalPharmacy,
        "medical" to Icons.Filled.MedicalServices,
        "hospital" to Icons.Filled.LocalHospital,
        "school" to Icons.Filled.School,
        "book" to Icons.Filled.MenuBook,
        "camera" to Icons.Filled.PhotoCamera,
        "brush" to Icons.Filled.Brush,
        "repair" to Icons.Filled.Build,
        "gift" to Icons.Filled.Redeem,
        "volunteer" to Icons.Filled.VolunteerActivism,
        "star" to Icons.Filled.Star,
        "favorite" to Icons.Filled.FavoriteBorder,
        // 收入与账户
        "salary" to Icons.Filled.Payments,
        "bonus" to Icons.Filled.EmojiEvents,
        "invest" to Icons.AutoMirrored.Filled.TrendingUp,
        "parttime" to Icons.Filled.Work,
        "redpack" to Icons.Filled.CardGiftcard,
        "other_income" to Icons.Filled.CurrencyYen,
        "wallet" to Icons.Filled.Wallet,
        "virtual" to Icons.Filled.AccountBalanceWallet,
        "bank" to Icons.Filled.AccountBalance,
        "card" to Icons.Filled.CreditCard,
        "savings" to Icons.Filled.Savings,
        "money" to Icons.Filled.AttachMoney,
        // 兜底
        "other" to Icons.Filled.MoreHoriz,
        "replay" to Icons.Filled.Replay
    )

    val keys: List<String> = map.keys.toList()

    fun of(key: String?): ImageVector = map[key] ?: Icons.Filled.MoreHoriz
}
