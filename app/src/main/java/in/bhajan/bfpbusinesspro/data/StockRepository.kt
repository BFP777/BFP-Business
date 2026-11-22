package `in`.bhajan.bfpbusinesspro.data

import androidx.room.withTransaction

/** Auditable opening-stock and manual adjustment operations. */
class StockRepository(private val database: BfpDatabase) {
    private val dao = database.dao()

    suspend fun insertItemWithOpeningStock(item: ItemEntity): Long = database.withTransaction {
        require(item.quantity >= 0.0 && item.quantity.isFinite()) { "Opening Stock मात्रा सही नहीं है" }
        val itemId = dao.insertItem(item)
        dao.insertStockMovement(StockMovementEntity(itemId = itemId, movementType = "OPENING", quantityDelta = item.quantity, previousQuantity = 0.0, newQuantity = item.quantity, reason = "प्रारंभिक स्टॉक"))
        itemId
    }

    suspend fun adjust(itemId: Long, newQuantity: Double, reason: String) = database.withTransaction {
        require(newQuantity >= 0.0 && newQuantity.isFinite()) { "नई स्टॉक मात्रा शून्य या उससे अधिक और सही होनी चाहिए" }
        require(reason.isNotBlank()) { "स्टॉक बदलाव का कारण लिखें" }
        val item = dao.getItem(itemId) ?: error("आइटम नहीं मिला")
        val delta = newQuantity - item.quantity
        require(kotlin.math.abs(delta) > 0.0000001) { "स्टॉक में कोई बदलाव नहीं है" }
        dao.updateItem(item.copy(quantity = newQuantity))
        dao.insertStockMovement(StockMovementEntity(itemId = itemId, movementType = "ADJUSTMENT", quantityDelta = delta, previousQuantity = item.quantity, newQuantity = newQuantity, reason = reason.trim()))
    }
}
