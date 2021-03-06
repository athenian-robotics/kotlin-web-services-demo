package org.athenian

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.MoreExecutors
import com.squareup.moshi.Moshi
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.util.ValuesMap
import org.athenian.options.ServerOptions
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


data class Customer(val name: String, val address: String, val paid: Boolean) {
    val id = idCounter.getAndIncrement()

    override fun toString(): String {
        return "Customer(id=$id, name='$name', address='$address', paid=$paid)"
    }

    companion object {
        private val idCounter: AtomicInteger = AtomicInteger(1)
    }
}

class CustomerServer(val port: Int = 8080) : AbstractIdleService() {

    private val moshi = Moshi.Builder().build()
    private var customers = mutableListOf<Customer>(Customer("Bill Smith", "123 Main St", false),
                                                    Customer("Jane Jackson", "1245 Birch Ave", true),
                                                    Customer("Steve Stillwell", "433 Peach Lane", true),
                                                    Customer("Mary McKenna", "3454 Apple St", false))
    private var server: NettyApplicationEngine = embeddedServer(Netty, this.port) {
        install(DefaultHeaders)
        install(Compression)
        install(CallLogging)
        install(Routing) {
            get("/") {
                call.respondText("This is the root", ContentType.Text.Plain)
            }

            get("/plain-hello") {
                call.respondText("Hello World!", ContentType.Text.Plain)
            }

            get("/html-hello") {
                val text = """
                        <html>
                            <head>
                            </head>
                            <body>
                                <h1>Hello World!</h1>
                            </body>
                        </html>
                    """
                call.respondText(text, ContentType.Text.Html)
            }

            get("/customers") {
                val jsonAdapter = moshi.adapter<List<Customer>>(List::class.java)
                call.respondText(jsonAdapter.toJson(customers), ContentType.Application.Json)
            }

            get("/customers/{id}") {
                val id = call.parameters["id"]?.toInt() ?: -1
                val cust: Customer? = customers.find { it.id == id }
                if (cust == null)
                    call.respond(HttpStatusCode.NotFound, "Customer not found")
                else {
                    val jsonAdapter = moshi.adapter<Customer?>(Customer::class.java)
                    call.respondText(jsonAdapter.toJson(cust), ContentType.Application.Json)
                }
            }

            get("/customer_query") {
                val name: String? = call.request.queryParameters["name"]
                if (name == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing name")
                }
                else {
                    val matches: List<Customer>? = customers.filter { it.name.contains(name) }
                    if (matches == null)
                        call.respond(HttpStatusCode.NotFound, "Customer not found")
                    else {
                        val jsonAdapter = moshi.adapter<List<Customer>?>(List::class.java)
                        call.respondText(jsonAdapter.toJson(matches), ContentType.Application.Json)
                    }
                }
            }

            post("/customers") {
                val map = call.receive<ValuesMap>()
                val name = map["name"]
                val address = map["address"] ?: ""
                val paid = map["paid"]?.toBoolean() ?: false

                if (name == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing name")
                }
                else {
                    val cust = Customer(name, address, paid)
                    customers.add(cust)
                    val jsonAdapter = moshi.adapter<Customer>(Customer::class.java)
                    call.respondText(jsonAdapter.toJson(cust), ContentType.Application.Json)
                }
            }
        }
    }

    init {
        this.addListener(GenericServiceListener(this), MoreExecutors.directExecutor())
    }

    override fun startUp() {
        logger.info("Starting server listening on port $port")
        this.server.start(wait = false)
    }

    override fun shutDown() {
        this.server.stop(1, 5, TimeUnit.SECONDS)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CustomerServer::class.java)
    }
}

fun main(args: Array<String>) {
    val options = ServerOptions(args)
    val server = CustomerServer(options.port)
    server.startAsync();
}

