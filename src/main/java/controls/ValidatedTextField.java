package controls;

import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

/**
 * Created by pozin_dn on 04.08.2018.
 */
public class ValidatedTextField<T> extends TextField {

    private static final PseudoClass validationError = PseudoClass.getPseudoClass("invalid");

    private final ReadOnlyBooleanWrapper validate;

    private final ObjectProperty<T> value;

    private final Validator<T> validator;
    private final StringConverter<T> converter;

    public ValidatedTextField(Validator<T> validator, StringConverter<T> converter) {
        getStylesheets().add("css/ValidatedTextField.css");
        getStyleClass().add("validation-field");

        this.validator = validator;
        this.converter = converter;

        validate = new ReadOnlyBooleanWrapper();

        value = new SimpleObjectProperty<>();

        final Tooltip tooltip = new Tooltip();

        textProperty().addListener((observable, oldValue, newValue) -> {
            boolean validated;
            try {
                final T v = getConverter().fromString(getText());
                validated = getValidator().validate(v);
                value.setValue(v);
            } catch (Exception ignored) {
                validated = false;
            }

            validate.setValue(validated);

            if (validated) {
                pseudoClassStateChanged(validationError, false);
                setTooltip(null);
            } else {
                pseudoClassStateChanged(validationError, true);
                final String str = validator.errorString();
                if (str == null)
                    setTooltip(null);
                else {
                    tooltip.setText(str);
                    setTooltip(tooltip);
                }
            }
        });

        value.addListener((observable, oldValue, newValue) -> {
            setText(getConverter().toString(newValue));
        });
    }

    public StringConverter<T> getConverter() {
        return converter;
    }

    public Validator<T> getValidator() {
        return validator;
    };

    public ReadOnlyBooleanProperty validateProperty() {
        return validate.getReadOnlyProperty();
    }

    public T getValue() {
        return value.get();
    }

    public void setValue(T value) {
        this.value.set(value);
    }

    public ObjectProperty<T> valueProperty() {
        return value;
    }
}
