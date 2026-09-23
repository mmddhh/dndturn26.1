package cc.sighs.dndturn.client;

import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/** Original vector pictograms, painted once per DOM generation through AUI's canvas. */
final class TacticalIcons {
    private TacticalIcons() {}

    static void append(Document document, Element button, String name) {
        Element node = document.createElement("canvas");
        node.setAttribute("class", "action-icon");
        node.setAttribute("width", "64");
        node.setAttribute("height", "64");
        Canvas canvas = (Canvas) button.appendChild(node);
        canvas.renderOperation(g -> {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(2, 2);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(switch (name) {
                case "attack", "break-block" -> 0xf0ae83;
                case "move", "dash", "disengage" -> 0xa7d6bf;
                case "dodge", "end" -> 0x9dd9ef;
                default -> 0xe5cb90;
            }));
            for (double[] points : paths(name)) {
                Path2D path = new Path2D.Double();
                path.moveTo(points[0], points[1]);
                for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
                g.draw(path);
            }
        });
    }

    private static double[][] paths(String name) {
        return switch (name) {
            case "attack" -> new double[][]{{7,26,23,10,27,5,21,7,6,23}, {8,17,15,24}, {5,24,8,27}};
            case "move" -> new double[][]{{7,25,12,21,10,17,6,18,5,22,7,25}, {19,16,24,12,23,7,19,7,17,12,19,16}, {14,8,14,3,9,3}, {14,3,8,9}};
            case "dash" -> new double[][]{{4,9,10,16,4,23}, {12,9,18,16,12,23}, {20,9,26,16,20,23}};
            case "dodge" -> new double[][]{{16,4,26,8,25,19,21,25,16,28,11,25,7,19,6,8,16,4}, {16,9,16,22}, {11,15,21,15}};
            case "disengage" -> new double[][]{{13,5,6,5,6,27,13,27}, {12,16,27,16,21,10}, {27,16,21,22}, {11,11,11,21}};
            case "place" -> new double[][]{{5,13,16,8,27,13,16,19,5,13,5,25,16,30,27,25,27,13}, {16,19,16,30}, {23,2,23,8}, {20,5,26,5}};
            case "break-block" -> new double[][]{{7,27,21,9}, {9,6,16,4,23,7,27,14}, {17,8,23,12}, {4,13,8,15}, {24,23,28,26}};
            case "use-block" -> new double[][]{{6,25,6,6,22,6,22,13}, {11,25,11,12,15,10,15,21,18,18,22,19,25,22,22,28,14,28,11,25}};
            case "use-item" -> new double[][]{{12,4,20,4,20,8,19,8,19,13,25,21,25,27,7,27,7,21,13,13,13,8,12,8,12,4}, {9,21,23,21}, {14,17,17,17}};
            case "behavior-hand" -> new double[][]{{5,10,25,10,20,5}, {25,10,20,15}, {27,22,7,22,12,17}, {7,22,12,27}};
            case "cancel-plan" -> new double[][]{{8,8,24,24}, {24,8,8,24}};
            case "inventory-toggle" -> new double[][]{{10,10,10,5,22,5,22,10}, {7,10,25,10,27,28,5,28,7,10}, {10,19,22,19,22,25,10,25,10,19}, {13,13,19,13}};
            case "exit" -> new double[][]{{14,5,6,5,6,27,14,27}, {13,16,28,16,22,10}, {28,16,22,22}};
            case "end" -> new double[][]{{12,7,23,16,12,25,12,7}, {27,7,27,25}};
            default -> new double[][]{{16,4,28,16,16,28,4,16,16,4}, {16,10,16,18}, {16,22,16,23}};
        };
    }
}
