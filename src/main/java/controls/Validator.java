package controls;

/**
 * Created by pozin_dn on 04.08.2018.
 */
public interface Validator<S> {

    boolean validate(S value);

    String errorString();
}
