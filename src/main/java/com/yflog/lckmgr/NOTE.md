## semantic
LockManager includes `server` and `client`. And I am using http protocol which uses short connection mostly.
And `server` supports following RPC:
1. tryLock
2. lock  (tryLock exactly)
3. unlock

And `lock` is simulated in `client` end.

## SESSION, CONNECTION, CLIENT
In my design, one `session` may include more than one connection, one `session` only works on one `client`. But one `client`
may have more than one `session` (meaning locked on more than one `locker`)
 

## BUGS
1. URL not encoded, cannot parse special chars like empty space
