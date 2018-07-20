package com.drestaurant.web

import com.drestaurant.common.domain.model.AuditEntry
import com.drestaurant.common.domain.model.Money
import com.drestaurant.common.domain.model.PersonName
import com.drestaurant.courier.domain.api.CreateCourierCommand
import com.drestaurant.customer.domain.api.CreateCustomerCommand
import com.drestaurant.order.domain.api.CreateOrderCommand
import com.drestaurant.order.domain.model.OrderInfo
import com.drestaurant.order.domain.model.OrderLineItem
import com.drestaurant.order.domain.model.OrderState
import com.drestaurant.query.FindCourierQuery
import com.drestaurant.query.FindCustomerQuery
import com.drestaurant.query.FindOrderQuery
import com.drestaurant.query.FindRestaurantQuery
import com.drestaurant.query.model.CourierEntity
import com.drestaurant.query.model.CustomerEntity
import com.drestaurant.query.model.OrderEntity
import com.drestaurant.query.model.RestaurantEntity
import com.drestaurant.query.repository.CourierRepository
import com.drestaurant.query.repository.CustomerRepository
import com.drestaurant.query.repository.OrderRepository
import com.drestaurant.query.repository.RestaurantRepository
import com.drestaurant.restaurant.domain.api.CreateRestaurantCommand
import com.drestaurant.restaurant.domain.model.MenuItem
import com.drestaurant.restaurant.domain.model.RestaurantMenu
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.queryhandling.QueryGateway
import org.axonframework.queryhandling.responsetypes.ResponseTypes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.rest.webmvc.RepositoryRestController
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import java.math.BigDecimal
import java.net.URI
import java.util.*
import java.util.function.Predicate
import javax.servlet.http.HttpServletResponse


/**
 * Repository REST Controller for handling 'commands' only
 *
 * Sometimes you may want to write a custom handler for a specific resource. To take advantage of Spring Data REST’s settings, message converters, exception handling, and more, we use the @RepositoryRestController annotation instead of a standard Spring MVC @Controller or @RestController
 */
@RepositoryRestController
class CommandController @Autowired constructor(private val commandGateway: CommandGateway, private val queryGateway: QueryGateway, private val entityLinks: RepositoryEntityLinks) {

    private val currentUser: String
        get() = if (SecurityContextHolder.getContext().authentication != null) {
            SecurityContextHolder.getContext().authentication.name
        } else "TEST"

    private val auditEntry: AuditEntry
        get() = AuditEntry(currentUser, Calendar.getInstance().time)

    @RequestMapping(value = "/customers", method = [RequestMethod.POST], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun createCustomer(@RequestBody request: CreateCustomerRequest, response: HttpServletResponse): ResponseEntity<Any> {
        val orderLimit = Money(request.orderLimit)
        val command = CreateCustomerCommand(PersonName(request.firstName, request.lastName), orderLimit, auditEntry)

        val queryResult = queryGateway.subscriptionQuery(
                FindCustomerQuery(command.targetAggregateIdentifier),
                ResponseTypes.instanceOf<CustomerEntity>(CustomerEntity::class.java),
                ResponseTypes.instanceOf<CustomerEntity>(CustomerEntity::class.java))
        try {
            val commandResult: String = commandGateway.sendAndWait(command)
            /* Returning the first update sent to our find customer query. */
            val customerEntity = queryResult.updates().blockFirst()
            return ResponseEntity.created(URI.create(entityLinks.linkToSingleResource(CustomerRepository::class.java, customerEntity?.id).href)).body(customerEntity)
        } finally {
            /* Closing the subscription query. */
            queryResult.close();
        }
    }

    @RequestMapping(value = "/couriers", method = [RequestMethod.POST], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun createCourier(@RequestBody request: CreateCourierRequest, response: HttpServletResponse): ResponseEntity<Any> {
        val command = CreateCourierCommand(PersonName(request.firstName, request.lastName), request.maxNumberOfActiveOrders, auditEntry)

        val queryResult = queryGateway.subscriptionQuery(
                FindCourierQuery(command.targetAggregateIdentifier),
                ResponseTypes.instanceOf<CourierEntity>(CourierEntity::class.java),
                ResponseTypes.instanceOf<CourierEntity>(CourierEntity::class.java))
        try {
            val commandResult: String = commandGateway.sendAndWait(command)
            /* Returning the first update sent to our find courier query. */
            val courierEntity = queryResult.updates().blockFirst()
            return ResponseEntity.created(URI.create(entityLinks.linkToSingleResource(CourierRepository::class.java, courierEntity?.id).href)).body(courierEntity)
        } finally {
            /* Closing the subscription query. */
            queryResult.close();
        }

    }

    @RequestMapping(value = "/restaurants", method = [RequestMethod.POST], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun createRestaurant(@RequestBody request: CreateRestaurantRequest, response: HttpServletResponse): ResponseEntity<Any> {
        val menuItems = ArrayList<MenuItem>()
        for ((id, name, price) in request.menuItems) {
            val item = MenuItem(id, name, Money(price))
            menuItems.add(item)
        }
        val menu = RestaurantMenu(menuItems, "ver.0")
        val command = CreateRestaurantCommand(request.name, menu, auditEntry)

        val queryResult = queryGateway.subscriptionQuery(
                FindRestaurantQuery(command.targetAggregateIdentifier),
                ResponseTypes.instanceOf<RestaurantEntity>(RestaurantEntity::class.java),
                ResponseTypes.instanceOf<RestaurantEntity>(RestaurantEntity::class.java))

        try {
            val commandResult: String = commandGateway.sendAndWait(command)
            /* Returning the first update sent to our find restaurant query. */
            val restaurantEntity = queryResult.updates().blockFirst()
            return ResponseEntity.created(URI.create(entityLinks.linkToSingleResource(RestaurantRepository::class.java, restaurantEntity?.id).href)).body(restaurantEntity)
        } finally {
            /* Closing the subscription query. */
            queryResult.close();
        }
    }

    @RequestMapping(value = "/orders", method = [RequestMethod.POST], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun createOrder(@RequestBody request: CreateOrderRequest, response: HttpServletResponse): ResponseEntity<Any> {
        val lineItems = ArrayList<OrderLineItem>()
        for ((id, name, price, quantity) in request.orderItems) {
            val item = OrderLineItem(id, name, Money(price), quantity)
            lineItems.add(item)
        }
        val orderInfo = OrderInfo(request.customerId!!, request.restaurantId!!, lineItems)
        val command = CreateOrderCommand(orderInfo, auditEntry)

        val queryResult = queryGateway.subscriptionQuery(
                FindOrderQuery(command.targetAggregateIdentifier),
                ResponseTypes.instanceOf<OrderEntity>(OrderEntity::class.java),
                ResponseTypes.instanceOf<OrderEntity>(OrderEntity::class.java))

        try {
            val commandResult: String = commandGateway.sendAndWait(command)
            /* Returning the first update sent to our find order query. */
            val orderEntity = queryResult.updates().filter(Predicate { it.state.equals(OrderState.VERIFIED_BY_RESTAURANT)}).blockFirst()
            return ResponseEntity.created(URI.create(entityLinks.linkToSingleResource(OrderRepository::class.java, orderEntity?.id).href)).body(orderEntity)
        } finally {
            /* Closing the subscription query. */
            queryResult.close();
        }
    }

//    @RequestMapping(value = "/restaurant/order/{id}/markpreparedcommand", method = [RequestMethod.POST], consumes = [MediaType.APPLICATION_JSON_VALUE])
//    @ResponseStatus(value = HttpStatus.CREATED)
//    fun markRestaurantOrderAsPrepared(@PathVariable id: String, response: HttpServletResponse) {
//        val command = MarkRestaurantOrderAsPreparedCommand(id, auditEntry)
//        commandGateway.send(command, LoggingCallback.INSTANCE)
//    }
//
//    @RequestMapping(value = "/courier/{cid}/order/{oid}/assigncommand", method = [RequestMethod.POST], consumes = [MediaType.APPLICATION_JSON_VALUE])
//    @ResponseStatus(value = HttpStatus.CREATED)
//    fun assignOrderToCourier(@PathVariable cid: String, @PathVariable oid: String, response: HttpServletResponse) {
//        val command = AssignCourierOrderToCourierCommand(oid, cid, auditEntry)
//        commandGateway.send(command, LoggingCallback.INSTANCE)
//    }
//
//    @RequestMapping(value = "/courier/order/{id}/markdeliveredcommand", method = [RequestMethod.POST], consumes = [MediaType.APPLICATION_JSON_VALUE])
//    @ResponseStatus(value = HttpStatus.CREATED)
//    fun markCourierOrderAsDelivered(@PathVariable id: String, response: HttpServletResponse) {
//        val command = MarkCourierOrderAsDeliveredCommand(id, auditEntry)
//        commandGateway.send(command, LoggingCallback.INSTANCE)
//    }
}

/**
 * A request for creating a Courier
 */
data class CreateCourierRequest(val firstName: String, val lastName: String, val maxNumberOfActiveOrders: Int)

/**
 * A request for creating a Customer/Consumer
 */
data class CreateCustomerRequest(val firstName: String, val lastName: String, val orderLimit: BigDecimal)

/**
 * A request for creating a Restaurant
 */
data class CreateOrderRequest(val customerId: String?, val restaurantId: String?, val orderItems: List<OrderItemRequest>)

/**
 * A request for creating a Restaurant
 */
data class CreateRestaurantRequest(val name: String, val menuItems: List<MenuItemRequest>)

/**
 * A Menu item request
 */
data class MenuItemRequest(val id: String, val name: String, val price: BigDecimal)

/**
 * An Order item request
 */
data class OrderItemRequest(val id: String, val name: String, val price: BigDecimal, val quantity: Int)