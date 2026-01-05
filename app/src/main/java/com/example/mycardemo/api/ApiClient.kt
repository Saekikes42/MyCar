package com.example.mycardemo.api

import com.example.mycardemo.BuildConfig
import com.example.mycardemo.models.*
import com.google.gson.Gson
import com.google.gson.JsonParseException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** HTTP transport. Callbacks run on OkHttp threads; screens dispatch UI updates themselves. */
object ApiClient {
    @Volatile var token: String? = null
    private val baseUrl = BuildConfig.API_BASE_URL.toHttpUrl()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun imageUrl(path: String): String =
        if (path.isBlank()) "" else baseUrl.resolve(path)?.toString().orEmpty()

    private fun request(path: String, method: String = "GET", data: Any? = null): Request {
        val builder = Request.Builder().url(requireNotNull(baseUrl.resolve("api/$path")))
        token?.let { builder.header("Authorization", "Bearer $it") }
        if (method != "GET") builder.method(method, gson.toJson(data).toRequestBody(json))
        return builder.build()
    }

    private inline fun <reified T> execute(request: Request, crossinline callback: (T?, String) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Не удалось подключиться к серверу")
            }

            override fun onResponse(call: Call, response: Response) {
                var error = ""
                val result: T? = response.use {
                    try {
                        val body = it.body?.string().orEmpty()
                        if (it.isSuccessful) {
                            gson.fromJson(body, T::class.java)
                        } else {
                            error = runCatching {
                                gson.fromJson(body, ApiError::class.java)?.message
                            }.getOrNull() ?: "Ошибка сервера: ${it.code}"
                            null
                        }
                    } catch (e: JsonParseException) {
                        error = "Некорректный ответ сервера"
                        null
                    } catch (e: IOException) {
                        error = "Ошибка чтения ответа сервера"
                        null
                    }
                }
                callback(result, error)
            }
        })
    }

    fun register(user: User, callback: (Boolean, String, UserData?) -> Unit) {
        execute<AuthResponse>(request("Auth/Register", "POST", user)) { result, error ->
            callback(result?.success == true, result?.message ?: error, result?.user)
        }
    }

    fun login(loginRequest: LoginRequest, callback: (Boolean, String, UserData?) -> Unit) {
        execute<AuthResponse>(request("Auth/Login", "POST", loginRequest)) { result, error ->
            if (result?.success == true) token = result.token
            callback(result?.success == true, result?.message ?: error, result?.user)
        }
    }

    fun getProfile(userId: Int, callback: (Boolean, UserData?) -> Unit) {
        execute<AuthResponse>(request("Auth/GetProfile/$userId")) { result, _ ->
            callback(result?.success == true, result?.user)
        }
    }

    fun logout() {
        execute<ActionResponse>(request("Auth/Logout", "POST", emptyMap<String, String>())) { _, _ -> }
    }

    fun updateProfile(userId: Int, update: UpdateProfileRequest, callback: (Boolean, String, UserData?) -> Unit) {
        execute<AuthResponse>(request("Auth/UpdateProfile/$userId", "PUT", update)) { result, error ->
            callback(result?.success == true, result?.message ?: error, result?.user)
        }
    }

    fun getCars(callback: (Boolean, List<Car>?) -> Unit) {
        execute<Array<Car>>(request("car")) { result, _ -> callback(result != null, result?.toList()) }
    }

    fun getCarById(carId: Int, callback: (Boolean, Car?) -> Unit) {
        execute<Car>(request("car/$carId")) { result, _ -> callback(result != null, result) }
    }

    fun getCart(userId: Int, callback: (Boolean, List<CartItem>?, Int, Double) -> Unit) {
        execute<CartResponse>(request("Cart/GetCart/$userId")) { result, _ ->
            callback(result != null, result?.items, result?.totalQuantity ?: 0, result?.totalPrice ?: 0.0)
        }
    }

    fun addToCart(cartItem: CartItem, userId: Int, callback: (Boolean) -> Unit) {
        execute<ActionResponse>(request("Cart/AddToCart", "POST", CartRequest(userId, cartItem))) { result, _ ->
            callback(result?.success == true)
        }
    }

    fun updateQuantity(userId: Int, cartItem: CartItem, callback: (Boolean) -> Unit) {
        execute<ActionResponse>(request("Cart/UpdateQuantity", "PUT", CartRequest(userId, cartItem))) { result, _ ->
            callback(result?.success == true)
        }
    }

    fun createOrder(orderRequest: OrderRequest, callback: (Boolean) -> Unit) {
        execute<ActionResponse>(request("Order/CreateOrder", "POST", orderRequest)) { result, _ ->
            callback(result?.success == true)
        }
    }

    fun getOrders(userId: Int, callback: (Boolean, List<Order>?) -> Unit) {
        execute<Array<Order>>(request("Order/GetOrders/$userId")) { result, _ ->
            callback(result != null, result?.toList())
        }
    }

    private data class ApiError(val message: String?)
    private data class ActionResponse(val success: Boolean)
    private data class CartResponse(val items: List<CartItem>, val totalQuantity: Int, val totalPrice: Double)
    private data class CartRequest(val userId: Int, val cartItem: CartItem)
}
