package com.hmdp.utils.interf;

public interface ILock {

    public boolean tryLock(Long timeOutSec);

    public void unlock();
}
