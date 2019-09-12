package deltatwoforce.mcmultimedia.renderer;

public enum PlaceholderIndex {
	DEFAULT(0),
	STEAM(1),
	REDDIT(2),
	YOUTUBE_DOWNLOADING(3),
	YOUTUBE(4);
	
	int num = 0;
	
	PlaceholderIndex(int index){
		num = index;
	}
	
	public int getIndex() {
		return num;
	}
}
