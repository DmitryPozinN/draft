package controls;

import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.util.StringConverter;

/**
 * Created by pozin_dn on 04.08.2018.
 */
public class IntField extends ValidatedTextField<Integer> {

    public IntField(int max) {
        this(new SimpleObjectProperty<>(max));
    }

    public IntField(ObjectProperty<Integer> max) {
        super(
                new Validator<Integer>() {
                    @Override
                    public boolean validate(Integer value) {
                        return value <= max.get();
                    }

                    @Override
                    public String errorString() {
                        return String.format("Значение должно быть не больше %d", max.get());
                    }
                },
                new StringConverter<Integer>() {
                    @Override
                    public String toString(Integer object) {
                        return object.toString();
                    }

                    @Override
                    public Integer fromString(String string) {
                        return Integer.valueOf(string);
                    }
                }
        );

        textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        setAlignment(Pos.CENTER);
    }
}
