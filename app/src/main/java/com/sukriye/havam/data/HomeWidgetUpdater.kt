package com.sukriye.havam.data

// Favoriler değişince ana ekran widget'ının yenilenmesi için. ViewModel Context tutmasın diye arayüz.
interface HomeWidgetUpdater {
    suspend fun refresh()
}
