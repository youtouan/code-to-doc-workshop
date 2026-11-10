package jp.co.tsubame.wholesale.web;

import java.util.HashSet;
import java.util.Set;
import jp.co.tsubame.wholesale.common.DispatchConfirmationCommand;
import jp.co.tsubame.wholesale.common.DispatchManifestCommand;
import jp.co.tsubame.wholesale.common.DispatchStopCommand;
import jp.co.tsubame.wholesale.common.DispatchTrackingCommand;
import jp.co.tsubame.wholesale.web.form.DispatchForm;

public final class DispatchInputs {
    private DispatchInputs() { }
    public static DispatchManifestCommand plan(DispatchForm form) {
        DispatchManifestCommand command = new DispatchManifestCommand();
        command.setId(Inputs.optionalId(form.getId(), "配送表"));
        command.setExpectedVersion(Inputs.integer(form.getVersion(), "更新番号", 0, Integer.MAX_VALUE));
        command.setWarehouseId(Inputs.id(form.getWarehouseId(), "配送元倉庫"));
        command.setCarrier(Inputs.choice(form.getCarrier(), "配送区分", "OWN", "PARCEL", "FREIGHT"));
        command.setPlannedDispatchDate(Inputs.date(form.getPlannedDispatchDate(), "配送予定日", false));
        command.setNote(Inputs.text(form.getNote(), "配送備考", 500, false));
        Inputs.arrays(form.getShipmentChoice(), form.getStopNote());
        Set<Long> selected = new HashSet<Long>();
        for (int i = 0; i < form.getShipmentChoice().length; i++) {
            String choice = form.getShipmentChoice()[i];
            String note = Inputs.text(form.getStopNote()[i], "配送先備考", 250, false);
            if ((choice == null || choice.length() == 0) && note.length() == 0) { continue; }
            DispatchStopCommand stop = choice(choice);
            if (!selected.add(stop.getShipmentId())) { throw Inputs.invalid("出荷指示", "同じ出荷指示が重複しています。"); }
            stop.setNote(note); command.getStops().add(stop);
        }
        return command;
    }
    public static DispatchStopCommand choice(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}:(0|[1-9][0-9]{0,9})")) {
            throw Inputs.invalid("出荷選択", "候補一覧の出荷ID:更新番号を指定してください。");
        }
        String[] parts = value.split(":", -1);
        DispatchStopCommand stop = new DispatchStopCommand();
        stop.setShipmentId(Inputs.id(parts[0], "出荷ID"));
        stop.setExpectedShipmentVersion(Inputs.integer(parts[1], "出荷更新番号", 0, Integer.MAX_VALUE)); return stop;
    }
    public static DispatchConfirmationCommand confirmation(DispatchForm form) {
        DispatchConfirmationCommand command = new DispatchConfirmationCommand();
        command.setDispatchDate(Inputs.date(form.getDispatchDate(), "実出荷日", false));
        Inputs.arrays(form.getShipmentId(), form.getTrackingReference()); Inputs.uniqueIds(form.getShipmentId(), "出荷");
        for (int i = 0; i < form.getShipmentId().length; i++) {
            DispatchTrackingCommand tracking = new DispatchTrackingCommand();
            tracking.setShipmentId(Inputs.id(form.getShipmentId()[i], "出荷"));
            tracking.setTrackingReference(Inputs.text(form.getTrackingReference()[i], "実際の送り状・自社配送管理番号", 80, true));
            command.getTracking().add(tracking);
        }
        return command;
    }
}
