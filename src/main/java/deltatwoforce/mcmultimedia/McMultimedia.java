package deltatwoforce.mcmultimedia;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import com.ibasco.agql.protocols.valve.steam.webapi.SteamWebApiClient;
import com.ibasco.agql.protocols.valve.steam.webapi.interfaces.SteamPlayerService;
import com.ibasco.agql.protocols.valve.steam.webapi.pojos.SteamPlayerOwnedGame;
import com.sapher.youtubedl.YoutubeDL;
import com.sapher.youtubedl.YoutubeDLException;
import com.sapher.youtubedl.YoutubeDLRequest;

import deltatwoforce.mcmultimedia.renderer.PlaceholderIndex;
import deltatwoforce.mcmultimedia.renderer.ScreenRenderer;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.SubredditSort;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthHelper;
import net.dean.jraw.pagination.DefaultPaginator;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;

public class McMultimedia extends JavaPlugin implements Listener
{
	private SteamWebApiClient steamApiClient;
	private RedditClient redditClient;
	private List<SteamPlayerOwnedGame> ownedGames = Arrays.asList();
	private DefaultPaginator<Submission> submissions;
	private Iterator<Submission> currentListing;
	private ScreenRenderer renderer;
	private MapView view;
	private MultimediaState state = MultimediaState.NOTHING;
	private boolean steamEnabled = false;
	private boolean redditEnabled = false;
	private boolean youtubeEnabled = false;
	private boolean error = false;
	private Inventory menuInventory;
	private ArrayList<Inventory> steamInventoryPages = new ArrayList<Inventory>();
	
	@Override
	public void onEnable() {
		this.getServer().getPluginManager().registerEvents(this, this);
		
		this.saveDefaultConfig();
		this.reloadConfig();
		
		if(!this.getConfig().getBoolean("configured")) {
			this.getLogger().log(Level.SEVERE, "Please configure McMultimedia using the config.yml file!");
			disablePlugin();
			return;
		}
		
		if(this.getConfig().getBoolean("steam_enabled")) {
			loadSteam();
			steamEnabled = true;
		}
		
		if(this.getConfig().getBoolean("reddit_enabled")) {
			loadReddit();
			redditEnabled = true;
		}
		
		if(this.getConfig().getBoolean("youtube_enabled")) {
			youtubeEnabled = true;
		}
		
		renderer = new ScreenRenderer();
		view = Bukkit.createMap(this.getServer().getWorlds().get(this.getServer().getWorlds().size()-1));
		view.addRenderer(renderer);
		
		menuInventory = createMenu();
		
		if(!error) {
			log(Level.INFO, "McMultimedia is up and running!");
			log(Level.INFO, "v" + getDescription().getVersion() + " by u/DeltaTwoForce on Reddit");
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(sender instanceof Player) {
			Player p = (Player) sender;
			
			if(command.getName().equals("steamtest")) {
				for(SteamPlayerOwnedGame app : ownedGames) {
					p.sendMessage(ChatColor.YELLOW + "- " + app.getName());
				}
				p.sendMessage(ChatColor.GOLD + "" + ownedGames.size() + " games found.");
				p.sendMessage(ChatColor.YELLOW + "If there weren't any games in the list, you either entered the wrong SteamID, wrong Steam Web API token or didn't enable Steam in the config.");
			}else if(command.getName().equals("multimedia")) {
				if(args.length == 0) {
					ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
					MapMeta mapMeta = (MapMeta) mapItem.getItemMeta();
					mapMeta.setMapView(view);
					mapItem.setItemMeta(mapMeta);
					
					p.getInventory().addItem(mapItem);
				}else {
					if(args[0].equals("cfg") && state == MultimediaState.NOTHING) {
						p.openInventory(menuInventory);
					}else if(args[0].equals("steam") && state == MultimediaState.STEAM) {
						p.openInventory(steamInventoryPages.get(0));
					}else if(args[0].equals("reddit") && state == MultimediaState.REDDIT) {
						if(args.length == 1) {
							p.sendMessage(ChatColor.RED + "/multimedia reddit <subreddit>");
						}else {
							String sub = args[1];
							if(sub.startsWith("r/")) {
								sub = sub.replaceFirst("r/", "");
							}
							
							submissions = redditClient.subreddit(sub).posts().sorting(SubredditSort.HOT).build();
							currentListing = submissions.next().iterator();
							redditTick(p);
						}
					}else if(args[0].equals("yt") && state == MultimediaState.YOUTUBE) {
						if(args.length == 1) {
							p.sendMessage(ChatColor.RED + "/multimedia yt <url>");
						}else {
							p.sendMessage(ChatColor.YELLOW + "Cleaning up residue...");
							
							File dir = new File(".ydl");
							if(dir.exists()) {
								try {
									FileUtils.deleteDirectory(dir);
									dir.mkdir();
								} catch (IOException e) {
									p.sendMessage(ChatColor.RED + ExceptionUtils.getStackTrace(e));
									p.sendMessage(ChatColor.RED + "An error has occured! Please send this error message, which has also been printed to the console, and report the issue on the GitHub page!");
									e.printStackTrace();
									return false;
								}
							}else {
								dir.mkdir();
							}
							
							new Thread(new Runnable() {
								
								@Override
								public void run() {
									p.sendMessage(ChatColor.YELLOW + "Downloading video...");
									renderer.placeholderIndex = PlaceholderIndex.YOUTUBE_DOWNLOADING;
									try {
										YoutubeDL.execute(new YoutubeDLRequest(args[1], dir.getPath()));
									} catch (YoutubeDLException e) {
										p.sendMessage(ChatColor.RED + ExceptionUtils.getStackTrace(e));
										p.sendMessage(ChatColor.RED + "An error has occured! Please send this error message, which has also been printed to the console, and report the issue on the GitHub page!");
										e.printStackTrace();
									}
									
									p.sendMessage(ChatColor.YELLOW + "Slicing video...");
									
									File vd = new File(dir,"vid");
									vd.mkdir();
									
									try {
										for(File f : dir.listFiles()) {
											if(f.isFile()) {
												new FFmpeg().run(new FFmpegBuilder().addInput(f.getPath()).addOutput(vd.getPath() + File.separator + "img%06d.png").setVideoResolution(128, 128).done());
											}
										}
									} catch (IOException e) {
										p.sendMessage(ChatColor.RED + ExceptionUtils.getStackTrace(e));
										p.sendMessage(ChatColor.RED + "An error has occured! Please send this error message, which has also been printed to the console, and report the issue on the GitHub page!");
										e.printStackTrace();
									}
									
									renderer.imageList = new ArrayList<>();
									renderer.imageList.addAll(Arrays.asList(vd.listFiles()));
									
									p.sendMessage(ChatColor.GREEN + "Enjoy the video!");
								}
							}).start();
						}
					}else if(args[0].equals("redditnext") && state == MultimediaState.REDDIT) {
						redditTick(p);
					}else if(args[0].equals("menu")) {
						setToMenu();
					}
				}
			}
		}else {
			sender.sendMessage(ChatColor.RED + "McMultimedia commands must be executed by a player.");
		}
		return true;
	}
	
	@Override
	public void onDisable() {
		ownedGames.clear();
		steamInventoryPages.clear();
		ownedGames.clear();
		view.getRenderers().forEach(view::removeRenderer);
		this.error = false;
		this.steamEnabled = false;
		this.redditEnabled = false;
		this.youtubeEnabled = false;
		this.getLogger().log(Level.INFO, "Goodbye!");
	}
	
	@EventHandler
	public void eventBoi(InventoryClickEvent e) {
		if(e.getClickedInventory().equals(menuInventory)) {
			e.setCancelled(true);
			if(e.getCurrentItem().getItemMeta().getDisplayName().contains("Steam")) {
				e.getWhoClicked().closeInventory();
				state = MultimediaState.STEAM;
				renderer.placeholderIndex = PlaceholderIndex.STEAM;
				e.getWhoClicked().sendMessage(ChatColor.GREEN + "McMultimedia is now set to Steam.");
			}else if(e.getCurrentItem().getItemMeta().getDisplayName().contains("Reddit")) {
				e.getWhoClicked().closeInventory();
				state = MultimediaState.REDDIT;
				renderer.placeholderIndex = PlaceholderIndex.REDDIT;
				e.getWhoClicked().sendMessage(ChatColor.GREEN + "McMultimedia is now set to Reddit.");
			}else if(e.getCurrentItem().getItemMeta().getDisplayName().contains("YouTube")) {
				e.getWhoClicked().closeInventory();
				state = MultimediaState.YOUTUBE;
				renderer.placeholderIndex = PlaceholderIndex.YOUTUBE;
				e.getWhoClicked().sendMessage(ChatColor.GREEN + "McMultimedia is now set to YouTube.");
			}
		}
		
		if(steamInventoryPages.contains(e.getClickedInventory())) {
			e.setCancelled(true);
			if(e.getCurrentItem().getItemMeta().getDisplayName().contains("Page")) {
				int page = Integer.parseInt(e.getCurrentItem().getItemMeta().getLore().get(0));
				e.getWhoClicked().openInventory(steamInventoryPages.get(page-1));
			}else {
				try {
					Desktop.getDesktop().browse(new URI("steam://run/" + e.getCurrentItem().getItemMeta().getLore().get(0)));
					e.getWhoClicked().closeInventory();
					e.getWhoClicked().sendMessage(ChatColor.GREEN + "Starting " + e.getCurrentItem().getItemMeta().getDisplayName() + "!");
				} catch (IOException e1) {
					e1.printStackTrace();
				} catch (URISyntaxException e1) {
					e1.printStackTrace();
				}
				renderer.drawScreen = true;
			}
		}
	}
	
	public void setToMenu() {
		state = MultimediaState.NOTHING;
		renderer.placeholderIndex = PlaceholderIndex.DEFAULT;
		renderer.drawScreen = false;
		renderer.render = null;
		renderer.imageList = null;
	}
	
	private Inventory createMenu() {
		Inventory inv = Bukkit.createInventory(null, InventoryType.HOPPER, colorCode("e")+"McMultimedia Menu");
		if(steamEnabled) {
			inv.addItem(createSteamStack());
		}
		if(redditEnabled) {
			inv.addItem(createRedditStack());
		}
		if(youtubeEnabled) {
			inv.addItem(createYoutubeStack());
		}
		return inv;
	}
	
	public static String colorCode(String color) {
        return (char) (0xfeff00a7) + color;
    }
	
	private void redditTick(Player user) {
		user.sendMessage(ChatColor.YELLOW + "Loading post, please wait...");
		if(!currentListing.hasNext()) {
			currentListing = submissions.next().iterator();
		}
		Submission sub = currentListing.next();
		
		while(sub.isSelfPost()) {
			if(!currentListing.hasNext()) {
				currentListing = submissions.next().iterator();
			}
			sub = currentListing.next();
		}
		
		try {
			BufferedImage img = ImageIO.read(new URL(sub.getUrl()));
			BufferedImage rndr = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
			rndr.createGraphics().drawImage(img, 0, 0, 128, 128, null);
			renderer.render = rndr;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		for(int i = 0;i<20;i++) {
			user.sendMessage(" ");
		}
		
		user.sendMessage(ChatColor.BLUE + "---------------------------------------------");
		user.sendMessage(ChatColor.YELLOW + sub.getTitle());
		user.sendMessage(ChatColor.YELLOW + "by u/" + sub.getAuthor() + " | " + sub.getScore() + " karma");
		TextComponent nextPost = new TextComponent(ChatColor.GOLD + "[NEXT POST] ");
		nextPost.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/multimedia redditnext"));
		nextPost.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] {new TextComponent("Click me to get to the next post!")}));
		TextComponent directUrl = new TextComponent(ChatColor.GOLD + "[DIRECT POST URL] ");
		directUrl.setClickEvent(new ClickEvent(Action.OPEN_URL, "http://reddit.com/" + sub.getPermalink()));
		directUrl.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] {new TextComponent("Click me to get this post's direct URL!")}));
		TextComponent home = new TextComponent(ChatColor.GOLD + "[BACK TO MENU]");
		home.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/multimedia menu"));
		home.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] {new TextComponent("Click me to choose another type of multimedia!")}));
		directUrl.addExtra(home);
		nextPost.addExtra(directUrl);
		user.spigot().sendMessage(nextPost);
		user.sendMessage(ChatColor.BLUE + "---------------------------------------------");
	}
	
	private void loadReddit() {
		if(!this.getConfig().getString("reddit_client_secret").toLowerCase().contains("dummy text")) {
			this.getLogger().log(Level.INFO, "Logging in to reddit...");
			Credentials creds = Credentials.script(this.getConfig().getString("reddit_username"), this.getConfig().getString("reddit_password"), this.getConfig().getString("reddit_client_id"), this.getConfig().getString("reddit_client_secret"));
			UserAgent agent = new UserAgent("bot", "mcmultimedia", this.getDescription().getVersion(), this.getConfig().getString("reddit_username"));
			
			redditClient = OAuthHelper.automatic(new OkHttpNetworkAdapter(agent), creds);
			this.getLogger().log(Level.INFO, "Logged in to reddit!");
		}else {
			log(Level.SEVERE, "Please enter your Reddit credentials into the config file and then reload.  Shutting down!");
			error = true;
			disablePlugin();
			return;
		}
	}
	
	private void loadSteam() {
		if(!this.getConfig().getString("steam_auth_token").toLowerCase().contains("dummy text")) {
			steamApiClient = new SteamWebApiClient(this.getConfig().getString("steam_auth_token"));
			SteamPlayerService sps = new SteamPlayerService(steamApiClient);
			
			this.getLogger().log(Level.INFO, "Loading all owned steam games...");
			try {
				ownedGames = sps.getOwnedGames(this.getConfig().getLong("steam_id"), true, true).get();
				log(Level.INFO, "Done loading all games! Games found: " + ownedGames.size());
				
				log(Level.INFO, "Sorting games by alphabetical order...");
				Collections.sort(ownedGames, new Comparator<SteamPlayerOwnedGame>() {
					public int compare(SteamPlayerOwnedGame o1, SteamPlayerOwnedGame o2) {
						return o1.getName().compareToIgnoreCase(o2.getName());
					}
				});
				log(Level.INFO, "Done sorting games! (Steam is set up!)");
				log(Level.INFO, "Creating inventory pages for Steam...");
				
				Inventory currentPage = Bukkit.createInventory(null, InventoryType.CHEST, colorCode("9") + "Steam Menu Page 1");
				int page = 1;
				for(int i = 0;i<ownedGames.size()-1;i++) {
					currentPage.addItem(createSteamGameStack(ownedGames.get(i)));
					
					int count = 0;
					for(ItemStack is : currentPage.getContents()) {
						if(is != null) {
							count++;
						}
					}
					
					if(count == 25 || i == ownedGames.size()-2) {
						for(int c = count;c<25;c++) {
							currentPage.addItem(createNamedThing(Material.LIGHT_GRAY_STAINED_GLASS_PANE, colorCode("8")+c, ""));
						}
						if(page == 1) {
							currentPage.addItem(createNamedThing(Material.GRAY_STAINED_GLASS_PANE, colorCode("8")+"p", ""));
						}else {
							currentPage.addItem(createNamedThing(Material.ORANGE_STAINED_GLASS_PANE, colorCode("6") + colorCode("l") + "Previous Page", ""+(page-1)));
						}
						page++;
						if(i == ownedGames.size()-2) {
							currentPage.addItem(createNamedThing(Material.GRAY_STAINED_GLASS_PANE, colorCode("8")+"n", ""));
						}else {
							currentPage.addItem(createNamedThing(Material.ORANGE_STAINED_GLASS_PANE, colorCode("6") + colorCode("l") + "Next Page", ""+(page)));
						}
						steamInventoryPages.add(currentPage);
						currentPage = Bukkit.createInventory(null, InventoryType.CHEST, colorCode("9") + "Steam Menu Page " + page);
					}
				}
				
				log(Level.INFO, "Done creating inventory pages!");
			} catch (InterruptedException e) {
				log(Level.SEVERE, "Error while loading games! (Wrong steam id?)");
				error = true;
				disablePlugin();
				e.printStackTrace();
				return;
			} catch (ExecutionException e) {
				log(Level.SEVERE, "Error while loading games! (Wrong steam id?)");
				error = true;
				disablePlugin();
				e.printStackTrace();
				return;
			}
		}else {
			log(Level.SEVERE, "Please enter your Steam Web API token and SteamID64 into the config file and then reload. Shutting down!");
			error = true;
			disablePlugin();
			return;
		}
	}
	
	private ItemStack createNamedThing(Material mat, String s, String lore) {
		ItemStack is = new ItemStack(mat);
		ItemMeta im = is.getItemMeta();
		im.setDisplayName(s);
		im.setLore(Arrays.asList(lore));
		is.setItemMeta(im);
		return is;
	}
	
	private ItemStack createSteamStack() {
		return createNamedThing(Material.BLUE_STAINED_GLASS_PANE, colorCode("9") + "Steam", "");
	}
	private ItemStack createRedditStack() {
		return createNamedThing(Material.ORANGE_STAINED_GLASS_PANE, colorCode("6") + "Reddit", "");
	}
	private ItemStack createYoutubeStack() {
		return createNamedThing(Material.RED_STAINED_GLASS_PANE, colorCode("c") + "YouTube", "");
	}
	private ItemStack createSteamGameStack(SteamPlayerOwnedGame spog) {
		return createNamedThing(Material.NETHER_STAR, colorCode("6") + spog.getName(), ""+spog.getAppId());
	}
	
	private void disablePlugin() {
		this.getServer().getPluginManager().disablePlugin(this);
	}
	
	private void log(Level level, String msg) {
		this.getLogger().log(level, msg);
	}
}
