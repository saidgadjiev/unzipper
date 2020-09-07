package ru.gadjini.telegram.unzipper.service.unzip;

import com.github.junrar.Junrar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import ru.gadjini.telegram.unzipper.condition.WindowsCondition;
import ru.gadjini.telegram.unzipper.exception.UnzipException;
import ru.gadjini.telegram.smart.bot.commons.service.format.Format;

import java.io.File;
import java.util.Set;

@Component
@Conditional(WindowsCondition.class)
public class RarLibUnzipDevice extends BaseUnzipDevice {

    @Autowired
    public RarLibUnzipDevice() {
        super(Set.of(Format.RAR));
    }

    public void unzip(int userId, String in, String out) {
        try {
            Junrar.extract(new File(in), new File(out));
        } catch (Exception e) {
            throw new UnzipException(e);
        }
    }
}
