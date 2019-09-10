package deltatwoforce.mcmultimedia.renderer;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class ScreenRenderer extends MapRenderer{
	public BufferedImage render;
	public boolean drawScreen;
	public PlaceholderIndex placeholderIndex;
	private Robot robot;
	private ArrayList<BufferedImage> placeholderImages;
	
	public ScreenRenderer() {
		placeholderIndex = PlaceholderIndex.DEFAULT;
		placeholderImages = new ArrayList<BufferedImage>();
		try {
			placeholderImages.add(ImageIO.read(this.getClass().getClassLoader().getResourceAsStream("placeholder.png")));
			placeholderImages.add(ImageIO.read(this.getClass().getClassLoader().getResourceAsStream("steamplaceholder.png")));
			placeholderImages.add(ImageIO.read(this.getClass().getClassLoader().getResourceAsStream("redditplaceholder.png")));
			placeholderImages.add(ImageIO.read(this.getClass().getClassLoader().getResourceAsStream("internetplaceholder.png")));
			placeholderImages.add(ImageIO.read(this.getClass().getClassLoader().getResourceAsStream("youtubeplaceholder.png")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			robot = new Robot();
		} catch (AWTException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void render(MapView map, MapCanvas canvas, Player player) {
		if(drawScreen) {
			BufferedImage screen = robot.createScreenCapture(new Rectangle(0, 0, 1920, 1080));
			BufferedImage screenRender = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
			screenRender.createGraphics().drawImage(screen, 0, 0, 128, 128, null);
			canvas.drawImage(0, 0, screenRender);
			screenRender = null;
			screen = null;
		}else {
			if(render == null) {
				canvas.drawImage(0, 0, placeholderImages.get(placeholderIndex.getIndex()));
			}else {
				canvas.drawImage(0, 0, render);
			}
		}
	}

}
