package jic.client

class OsObserver : Function1<Int, Unit> {

    private var bytesWritten = 0

    public operator override fun invoke(b: Int) {
        bytesWritten += 1
        if (bytesWritten % (1024 * 1024) == 0) {
            println("${bytesWritten / (1024 * 1024)} MB downloaded")
        }
    }

}