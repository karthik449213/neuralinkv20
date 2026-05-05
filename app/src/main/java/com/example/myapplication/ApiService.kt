package com.example.myapplication

import retrofit2.Call
import retrofit2.http.GET

data class HelloResponse(val message: String)

interface ApiService {

    @GET("hello")
    fun getHello(): Call<HelloResponse>
}