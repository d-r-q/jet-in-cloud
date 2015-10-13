package jic.client

import java.io.OutputStream

class ObservableOutputStream(val delegate: OutputStream, private val observer: (Int) -> Unit) : OutputStream() {

    override fun write(b: Int) {
        delegate.write(b)
        observer(b)
    }

}