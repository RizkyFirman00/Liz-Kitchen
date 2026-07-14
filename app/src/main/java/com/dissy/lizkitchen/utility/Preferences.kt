package com.dissy.lizkitchen.utility

import android.content.Context
import android.content.SharedPreferences
import com.dissy.lizkitchen.model.User
import com.google.firebase.auth.FirebaseAuth

object Preferences {
    const val ADMIN_EMAIL = "admin@lizkitchen.com"

    private const val PREF_NAME = "onSignIn"
    private const val ROLE_ADMIN = "admin"
    private const val ROLE_USER = "user"

    fun init(context: Context, name: String): SharedPreferences {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    private fun editor(context: Context, name: String): SharedPreferences.Editor {
        val sharedPref = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return sharedPref.edit()
    }

    fun saveAdminSession(context: Context) {
        FirebaseAuth.getInstance().signOut()
        editor(context, PREF_NAME)
            .putString("role", ROLE_ADMIN)
            .putString("email", ADMIN_EMAIL)
            .putString("name", "Admin")
            .remove("userId")
            .remove("username")
            .apply()
    }

    fun getUserId(context: Context): String? {
        val sharedPref = init(context, PREF_NAME)
        return sharedPref.getString("userId", null)
    }

    fun isAdminSession(context: Context): Boolean {
        val sharedPref = init(context, PREF_NAME)
        return sharedPref.getString("role", null) == ROLE_ADMIN
    }

    fun isUserSession(context: Context): Boolean {
        val authUser = FirebaseAuth.getInstance().currentUser
        return !isAdminSession(context) &&
            !getUserId(context).isNullOrBlank() &&
            authUser?.isEmailVerified == true
    }

    fun saveUserInfo(user: User, context: Context) {
        val editor = editor(context, PREF_NAME)
        editor.putString("role", ROLE_USER)
        editor.putString("userId", user.userId)
        editor.putString("email", user.email)
        editor.putString("name", user.name)
        editor.putString("phoneNumber", user.phoneNumber)
        editor.putString("alamat", user.alamat)
        editor.remove("username")
        editor.apply()
    }

    fun getUserInfo(context: Context): User? {
        val sharedPref = init(context, PREF_NAME)
        return User(
            userId = sharedPref.getString("userId", null) ?: "",
            name = sharedPref.getString("name", null)
                ?: sharedPref.getString("username", null)
                ?: "",
            email = sharedPref.getString("email", null) ?: "",
            phoneNumber = sharedPref.getString("phoneNumber", null) ?: "",
            alamat = sharedPref.getString("alamat", null) ?: "Belum Diisi"
        )
    }

    fun logout(context: Context){
        FirebaseAuth.getInstance().signOut()
        editor(context, PREF_NAME).clear().apply()
    }
}
