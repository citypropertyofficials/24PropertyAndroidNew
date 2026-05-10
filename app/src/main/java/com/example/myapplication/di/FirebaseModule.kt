package com.example.myapplication.di

import com.example.myapplication.data.repository.AuthRepository
import com.example.myapplication.data.repository.AuthRepositoryImpl
import com.example.myapplication.data.repository.FavoritesRepository
import com.example.myapplication.data.repository.FavoritesRepositoryImpl
import com.example.myapplication.data.repository.PropertyRepository
import com.example.myapplication.data.repository.PropertyRepositoryImpl
import com.example.myapplication.ui.screens.favorites.FavoritesViewModel
import com.example.myapplication.ui.screens.home.HomeViewModel
import com.example.myapplication.ui.screens.login.LoginViewModel
import com.example.myapplication.ui.screens.profile.ProfileViewModel
import com.example.myapplication.ui.screens.propertydetails.PropertyDetailsViewModel
import com.example.myapplication.ui.screens.splash.SplashViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import com.example.myapplication.data.repository.DashboardRepository
import com.example.myapplication.data.repository.DashboardRepositoryImpl
import org.koin.dsl.module
import com.example.myapplication.ui.screens.dashboard.DashboardViewModel
import com.example.myapplication.ui.screens.more.MoreViewModel
import org.koin.dsl.bind

val appModule = module {
    single { FirebaseAuth.getInstance() }
    single { FirebaseFirestore.getInstance() }
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    singleOf(::PropertyRepositoryImpl) bind PropertyRepository::class
    singleOf(::FavoritesRepositoryImpl) bind FavoritesRepository::class
    singleOf(::DashboardRepositoryImpl) bind DashboardRepository::class
    viewModelOf(::LoginViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::SplashViewModel)
    viewModelOf(::PropertyDetailsViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::MoreViewModel)
}
