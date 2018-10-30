package com.drestaurant.restaurant.domain.api

import com.drestaurant.common.domain.api.AuditableAbstractCommand
import com.drestaurant.common.domain.api.model.AuditEntry
import com.drestaurant.restaurant.domain.api.model.RestaurantMenu
import com.drestaurant.restaurant.domain.api.model.RestaurantOrderDetails
import org.axonframework.modelling.command.TargetAggregateIdentifier
import java.util.*
import javax.validation.Valid

/**
 * This command is used to construct new restaurant
 */
data class CreateRestaurantCommand(val name: String, @field:Valid val menu: RestaurantMenu, @TargetAggregateIdentifier val targetAggregateIdentifier: String, override val auditEntry: AuditEntry) : AuditableAbstractCommand(auditEntry) {

    constructor(name: String, menu: RestaurantMenu, auditEntry: AuditEntry) : this(name, menu, UUID.randomUUID().toString(), auditEntry)
}

/**
 * This command is used to construct new order in restaurant
 */
data class CreateRestaurantOrderCommand(@TargetAggregateIdentifier val targetAggregateIdentifier: String, @field:Valid val orderDetails: RestaurantOrderDetails, val restaurantId: String, override val auditEntry: AuditEntry) : AuditableAbstractCommand(auditEntry) {

    constructor(orderDetails: RestaurantOrderDetails, restaurantId: String, auditEntry: AuditEntry) : this(UUID.randomUUID().toString(), orderDetails, restaurantId, auditEntry)
}

/**
 * This command is used to mark restaurant order (targetAggregateIdentifier) as prepared
 */
data class MarkRestaurantOrderAsPreparedCommand(@TargetAggregateIdentifier val targetAggregateIdentifier: String, override val auditEntry: AuditEntry) : AuditableAbstractCommand(auditEntry)