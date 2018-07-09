package com.masabi.kotlin

class User_Builder_ForReals() {
    private var user: String? = null

    private var age: Int? = null

    fun withUser(user: String?): User_Builder_ForReals {
        this.user = user; return this}

    fun withAge(age: Int?): User_Builder_ForReals {
        this.age = age; return this}

    fun build(): User {
        return User(this.user!!, this.age!!)
    }
}