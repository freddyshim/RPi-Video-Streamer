package com.anookday.rpistream.extensions

import androidx.lifecycle.MutableLiveData

/**
 * Update a list LiveData with a new item.
 * @param item object to add to list
 */
fun <T> MutableLiveData<MutableList<T>>.addNewItem(item: T) {
    val oldValue = this.value ?: mutableListOf()
    oldValue.add(item)
    this.postValue(oldValue)
}