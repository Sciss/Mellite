import de.sciss.submin.Submin;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextField;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FocusTest implements Runnable {
    public static void main(String[] args) {
        EventQueue.invokeLater(new FocusTest());
    }

    public void run() {
        Submin.install(true);
        final JFrame f = new JFrame();
        final JButton invoker = new JButton("Pop");
        final JDialog pop = new JDialog();
        pop.setUndecorated(true);
        final JTextField text = new JTextField(12);
        pop.add(text);
        pop.pack();
        invoker.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                pop.setLocationRelativeTo(invoker);
                pop.setVisible(true);
            }
        });
        f.getContentPane().add(invoker);
        f.pack();
        f.setVisible(true);
    }
}
