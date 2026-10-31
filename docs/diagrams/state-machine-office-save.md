---
diagram: state-machine-office-save
uml-type: State Machine Diagram（狀態機圖 — OfficeSave 存檔生命週期）
source: docs/EDD.md §4.5.6 (2)
generated: 2026-10-30T00:00:00Z
---

# State Machine Diagram — OfficeSave / 存檔生命週期

> 來源：EDD.md §4.5.6（2）（PRD §6.3）

```mermaid
stateDiagram-v2
    [*] --> NoSave : accountCreated [always] / none
    NoSave --> Saved : putSave(data) [data!=null] / upsert(account_id,slot)
    Saved --> Saved : putSave(data) [data!=null] / upsert(updated_at=now)
    Saved --> Restored : getSave(slot) [exists] / returnData
    NoSave --> NoSave : getSave(slot) [notExists] / returnNull(非404)
    Restored --> Saved : putSave(data) [data!=null] / upsert
    note right of NoSave : 首次無存檔回 {save:null}，供客戶端判斷首次
```

**Invalid Transition Table（OfficeSave）：**

| From | To | Behavior | Log |
|------|----|----------|-----|
| any | Saved（data==null） | 拒絕，回 400「缺 data」，不寫入 | WARN |
