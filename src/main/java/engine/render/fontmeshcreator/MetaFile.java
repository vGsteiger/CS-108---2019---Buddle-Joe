package engine.render.fontmeshcreator;

import game.Game;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides functionality for getting the values from a font file.
 *
 * @author Karl
 */
class MetaFile {
  private static final int PAD_TOP = 0;
  private static final int PAD_LEFT = 1;
  private static final int PAD_BOTTOM = 2;
  private static final int PAD_RIGHT = 3;

  private static final int DESIRED_PADDING = 3;

  private static final String SPLITTER = " ";
  private static final String NUMBER_SEPARATOR = ",";

  private double aspectRatio;

  private double verticalPerPixelSize;
  private double horizontalPerPixelSize;
  private double spaceWidth;
  private int[] padding;
  private int paddingWidth;
  private int paddingHeight;

  private Map<Integer, Character> metaData = new HashMap<>();

  private BufferedReader reader;
  private Map<String, String> values = new HashMap<>();

  /**
   * Opens a font file in preparation for reading.
   *
   * @param file - the font file.
   */
  MetaFile(BufferedReader file) {
    this.aspectRatio = (double) Game.window.getWidth() / (double) Game.window.getHeight();
    openFile(file);
    loadPaddingData();
    loadLineSizes();
    int imageWidth = getValueOfVariable("scaleW");
    loadCharacterData(imageWidth);
    close();
  }

  double getSpaceWidth() {
    return spaceWidth;
  }

  Character getCharacter(int ascii) {
    return metaData.get(ascii);
  }

  /**
   * Read in the next line and store the variable values.
   *
   * @return {@code true} if the end of the file hasn't been reached.
   */
  private boolean processNextLine() {
    values.clear();
    String line = null;
    try {
      line = reader.readLine();
    } catch (IOException ignored) {
      // ignore IOEx
    }
    if (line == null) {
      return false;
    }
    for (String part : line.split(SPLITTER)) {
      String[] valuePairs = part.split("=");
      if (valuePairs.length == 2) {
        values.put(valuePairs[0], valuePairs[1]);
      }
    }
    return true;
  }

  /**
   * Gets the {@code int} value of the variable with a certain name on the current line.
   *
   * @param variable - the name of the variable.
   * @return The value of the variable.
   */
  private int getValueOfVariable(String variable) throws NumberFormatException {
    return Integer.parseInt(values.get(variable));
  }

  /**
   * Gets the array of ints associated with a variable on the current line.
   *
   * @param variable - the name of the variable.
   * @return The int array of values associated with the variable.
   */
  @SuppressWarnings("SameParameterValue")
  private int[] getValuesOfVariable(String variable) {
    String[] numbers = values.get(variable).split(NUMBER_SEPARATOR);
    int[] actualValues = new int[numbers.length];
    for (int i = 0; i < actualValues.length; i++) {
      actualValues[i] = Integer.parseInt(numbers[i]);
    }
    return actualValues;
  }

  /** Closes the font file after finishing reading. */
  private void close() {
    try {
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Opens the font file, ready for reading.
   *
   * @param file - the font file.
   */
  private void openFile(BufferedReader file) {
    try {
      reader = file;
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Couldn't read font meta file!");
    }
  }

  /** Loads the data about how much padding is used around each character in the texture atlas. */
  private void loadPaddingData() {
    processNextLine();
    this.padding = getValuesOfVariable("padding");
    this.paddingWidth = padding[PAD_LEFT] + padding[PAD_RIGHT];
    this.paddingHeight = padding[PAD_TOP] + padding[PAD_BOTTOM];
  }

  /**
   * Loads information about the line height for this font in pixels, and uses this as a way to find
   * the conversion rate between pixels in the texture atlas and screen-space.
   */
  private void loadLineSizes() {
    processNextLine();
    int lineHeightPixels = getValueOfVariable("lineHeight") - paddingHeight;
    verticalPerPixelSize = TextMeshCreator.LINE_HEIGHT / (double) lineHeightPixels;
    horizontalPerPixelSize = verticalPerPixelSize / aspectRatio;
  }

  /**
   * Loads in data about each character and stores the data in the {@link Character} class.
   *
   * @param imageWidth - the width of the texture atlas in pixels.
   */
  private void loadCharacterData(int imageWidth) {
    processNextLine();
    processNextLine();
    while (processNextLine()) {
      Character c = loadCharacter(imageWidth);
      if (c != null) {
        metaData.put(c.getId(), c);
      }
    }
  }

  /**
   * Loads all the data about one character in the texture atlas and converts it all from 'pixels'
   * to 'screen-space' before storing. The effects of padding are also removed from the data.
   *
   * @param imageSize - the size of the texture atlas in pixels.
   * @return The data about the character.
   */
  private Character loadCharacter(int imageSize) {
    int id;
    try {
      id = getValueOfVariable("id");
    } catch (NumberFormatException e) {
      return null;
    }

    if (id == TextMeshCreator.SPACE_ASCII) {
      this.spaceWidth = (getValueOfVariable("xadvance") - paddingWidth) * horizontalPerPixelSize;
      return null;
    }
    double texX =
        ((double) getValueOfVariable("x") + (padding[PAD_LEFT] - DESIRED_PADDING)) / imageSize;
    double texY =
        ((double) getValueOfVariable("y") + (padding[PAD_TOP] - DESIRED_PADDING)) / imageSize;
    int width = getValueOfVariable("width") - (paddingWidth - (2 * DESIRED_PADDING));
    int height = getValueOfVariable("height") - ((paddingHeight) - (2 * DESIRED_PADDING));
    double quadWidth = width * horizontalPerPixelSize;
    double quadHeight = height * verticalPerPixelSize;
    double texSizeX = (double) width / imageSize;
    double texSizeY = (double) height / imageSize;
    double offX =
        (getValueOfVariable("xoffset") + padding[PAD_LEFT] - DESIRED_PADDING)
            * horizontalPerPixelSize;
    double offY =
        (getValueOfVariable("yoffset") + (padding[PAD_TOP] - DESIRED_PADDING))
            * verticalPerPixelSize;
    double advanceX = (getValueOfVariable("xadvance") - paddingWidth) * horizontalPerPixelSize;
    return new Character(
        id, texX, texY, texSizeX, texSizeY, offX, offY, quadWidth, quadHeight, advanceX);
  }
}
