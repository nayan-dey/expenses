package com.kharcha.app.ui.icons

/** The icons offered for a category, in the groups the icon picker shows. */
object IconGroups {
    private fun group(name: String, icons: String): Pair<String, List<Ic>> =
        name to icons.split(' ').filter { it.isNotBlank() }.mapNotNull { wanted -> Ic.entries.firstOrNull { it.name == wanted } }

    val all: List<Pair<String, List<Ic>>> = listOf(
        group("Commerce", "shopping_bag shopping_cart credit_card redeem payments shopping_basket local_mall storefront sell receipt_long account_balance savings wallet attach_money currency_rupee paid request_quote point_of_sale barcode_scanner"),
        group("Food and drink", "restaurant local_cafe local_bar fastfood local_pizza bakery_dining icecream liquor ramen_dining lunch_dining local_grocery_store nutrition egg cake coffee water_drop kitchen set_meal tapas rice_bowl"),
        group("Transport", "directions_car local_gas_station train directions_bus flight two_wheeler pedal_bike local_taxi directions_walk local_parking ev_station directions_boat subway electric_rickshaw local_shipping commute toll car_repair"),
        group("Home", "home cottage apartment bed chair lightbulb bolt wifi router propane_tank build plumbing mop tv local_laundry_service electrical_services handyman yard garage_home home_repair_service cleaning_services"),
        group("Health and fitness", "fitness_center medical_services medication local_hospital healing monitor_heart spa self_improvement sports_soccer sports_cricket pool hiking dentistry psychology vaccines sports_tennis sports_basketball directions_run exercise favorite"),
        group("Fun", "movie music_note sports_esports theater_comedy casino headphones live_tv local_activity attractions book menu_book stadia_controller nightlife festival mic piano photo_camera palette celebration"),
        group("Shopping and style", "checkroom diamond watch phone_iphone laptop devices toys content_cut face styler dry_cleaning eyeglasses smartphone"),
        group("Work and money", "work business_center handshake school card_giftcard monetization_on volunteer_activism real_estate_agent trending_up trending_down work_history badge engineering currency_exchange percent emoji_events workspace_premium account_balance_wallet"),
        group("Family and life", "child_care pets family_restroom subscriptions phone_in_talk luggage beach_access hotel map temple_hindu mosque church sim_card cloud newspaper stroller elderly diversity_3 group person mail"),
        group("Signs", "list category label star flag push_pin key shield lock bolt arrow_upward arrow_downward swap_horiz sync repeat hourglass_top schedule event today history info help warning error check_circle add_circle cancel block circle auto_awesome rocket_launch science lightbulb tips_and_updates insights"),
    )
}
