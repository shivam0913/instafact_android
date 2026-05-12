package com.instafact.app.utils

import android.content.Context
import com.google.gson.Gson
import com.instafact.app.R
import com.instafact.app.data.model.ApiErrorResponse
import retrofit2.HttpException
import java.io.IOException

object ApiErrorParser {

    fun getMessage(context: Context, throwable: Throwable): String {
        return when (throwable) {
            is IllegalStateException -> throwable.message ?: context.getString(R.string.unknown_error)
            is IOException -> context.getString(R.string.no_internet_error)
            is HttpException -> {
                val detail = throwable.response()?.errorBody()?.string()
                val parsed = runCatching {
                    Gson().fromJson(detail, ApiErrorResponse::class.java)
                }.getOrNull()
                parsed?.detail ?: context.getString(R.string.unknown_error)
            }
            else -> throwable.message ?: context.getString(R.string.unknown_error)
        }
    }
}
