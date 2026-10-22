package com.yogeshpaliyal.deepr.ui.screens.home

@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.TYPE,
)
annotation class ViewType {
    companion object {
        const val LIST = 0
        const val GRID = 1
        const val COMPACT = 2
    }
}
