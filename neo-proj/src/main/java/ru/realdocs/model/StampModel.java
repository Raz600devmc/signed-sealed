package ru.realdocs.model;

import java.util.ArrayList;
import java.util.List;

public final class StampModel {
   public static final int SHAPE_CIRCLE = 0;
   public static final int SHAPE_OVAL = 1;
   public static final int SHAPE_RECT = 2;
   public static final int SHAPE_IMAGE = 3;
   public static final String[] SHAPE_NAMES = new String[]{"Круглая", "Овальная", "Прямоугольная", "Только картинка"};
   public static final int KIND_SEAL = 0;
   public static final int KIND_BORDER = 1;
   public static final int KIND_VISA = 2;
   public static final String[] KIND_NAMES = new String[]{"Печать", "Погранштамп", "Виза"};
   public static final String[] PRESET_NAMES = new String[]{"Синий", "Фиолетовый", "Красный", "Чёрный", "Зелёный"};
   public static final int[] PRESET_COLORS = new int[]{2046611, 5974662, 11740702, 1710618, 1993530};
   public String id = "";
   public String ownerUuid = "";
   public String ownerName = "";
   public String org = "";
   public int shape;
   public int kind;
   public int color = PRESET_COLORS[0];
   public String top = "";
   public String bottom = "";
   public String center = "";
   public boolean hasImage;
   public boolean approved;
   public boolean everApproved;
   public String created = "";
   public final List<DesignElement> visa = new ArrayList<>();

   public boolean pending() {
      return !this.approved && !this.everApproved;
   }

   public boolean revoked() {
      return !this.approved && this.everApproved;
   }

   public StampModel copy() {
      StampModel m = new StampModel();
      m.id = this.id;
      m.ownerUuid = this.ownerUuid;
      m.ownerName = this.ownerName;
      m.org = this.org;
      m.shape = this.shape;
      m.kind = this.kind;
      m.color = this.color;
      m.top = this.top;
      m.bottom = this.bottom;
      m.center = this.center;
      m.hasImage = this.hasImage;
      m.approved = this.approved;
      m.everApproved = this.everApproved;
      m.created = this.created;

      for (DesignElement e : this.visa) {
         m.visa.add(e.copy());
      }

      return m;
   }
}
