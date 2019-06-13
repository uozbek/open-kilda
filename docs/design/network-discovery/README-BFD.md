# Network discovery (BFD management)

## Assumption
* "system" can produce several "BFD-enable" request, but all of them must
  refer same ISL(sw+ports pair). To replace/reconfigure remote BFD
  endpoint - "system" must send BFD-disable (for old/existing configuration)
  and BFD-enable (for new/desire configuration).

## Particular qualities
### Track BFD logical port status
BFD logical port manager/controller must trace logical port status (UP/DOWN).
This tracking are done "outside" FSM, because if it done inside FSM it becomes
grows to big(x3 from current size). 

Draft of possible port tracking subsystem:
```java
public final class PortStatusMonitor {
    @Getter
    private LinkStatus status;

    private void update(LinkStatus update) {
        this.status = update;
    }
}
```

Instance of `PortStatusMonitor` is stored inside `BfdPortFsm`(so FSM can always
query actual port status from it). "Service layer" on port status update events
must update status into `PortStatusMonitor`(inside FSM) also it must fire
PORT_UP/PORT_DOWN events into `BfdPortFsm` instance.


### Logical port management
Logical port management are done via GRPCSpeaker via it's kafka-API. All GRPC calls
onside network topology will be implemented inside "worker"(in H&S terms), so all
calls will have guaranteed response (success/error/timeout).

Used GRPC calls:
* list logical ports (with port types) - used by `SwitchFsm`
* create logical port - used by `BfdPortFsm`
* delete logical port - used by `BfdPortFsm`
