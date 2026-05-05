package com.example.myapplication.di

import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthRepositoryImpl
import com.example.myapplication.ui.screens.home.HomeViewModel
import com.example.myapplication.ui.screens.login.LoginViewModel
import com.example.myapplication.ui.screens.profile.ProfileViewModel
import com.example.myapplication.ui.screens.splash.SplashViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    single { FirebaseAuth.getInstance() }
    single { FirebaseFirestore.getInstance() }
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    viewModelOf(::LoginViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SplashViewModel)
}
