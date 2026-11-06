var CLASS_FEATURES = {};

/* dndClasses class progression data.
 * Schema: CLASS_FEATURES["key"] = {
 *   levels:     [ { lv, items:[ { n:"中文名", e:"English", t:"可选描述" } ] } ],
 *   subclasses: [ { name, en, features:[ { lv, items:[...] } ] } ]
 * }
 * If "t" is omitted the page shows "<中文名>特性描述" as a placeholder.
 */
CLASS_FEATURES["barbarian"] = {
  levels: [
    { lv: 1, items: [ { n: "狂暴", e: "Rage" }, { n: "无甲防御", e: "Unarmored Defense" } ] },
    { lv: 2, items: [ { n: "鲁莽攻击", e: "Reckless Attack" }, { n: "危险感知", e: "Danger Sense" } ] },
    { lv: 3, items: [ { n: "原始道途", e: "Primal Path" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [ { n: "额外攻击", e: "Extra Attack" }, { n: "快速移动", e: "Fast Movement" } ] },
    { lv: 6, items: [ { n: "原始道途特性", e: "Primal Path feature" } ] },
    { lv: 7, items: [ { n: "野性直觉", e: "Feral Instinct" } ] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [ { n: "残暴重击（1骰）", e: "Brutal Critical (1 die)" } ] },
    { lv: 10, items: [ { n: "原始道途特性", e: "Primal Path feature" } ] },
    { lv: 11, items: [ { n: "无尽狂暴", e: "Relentless Rage" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [ { n: "残暴重击（2骰）", e: "Brutal Critical (2 dice)" } ] },
    { lv: 14, items: [ { n: "原始道途特性", e: "Primal Path feature" } ] },
    { lv: 15, items: [ { n: "持久狂暴", e: "Persistent Rage" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "残暴重击（3骰）", e: "Brutal Critical (3 dice)" } ] },
    { lv: 18, items: [ { n: "不屈威能", e: "Indomitable Might" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "原始勇士", e: "Primal Champion" } ] }
  ],
  subclasses: [
    {
      name: "狂战士道途", en: "Path of the Berserker",
      features: [
        { lv: 3, items: [ { n: "狂乱", e: "Frenzy" } ] },
        { lv: 6, items: [ { n: "无心狂暴", e: "Mindless Rage" } ] },
        { lv: 10, items: [ { n: "威吓之姿", e: "Intimidating Presence" } ] },
        { lv: 14, items: [ { n: "报复", e: "Retaliation" } ] }
      ]
    },
    {
      name: "图腾战士道途", en: "Path of the Totem Warrior",
      features: [
        { lv: 3, items: [ { n: "灵魂追寻", e: "Spirit Seeker" }, { n: "图腾之魂", e: "Totem Spirit" } ] },
        { lv: 6, items: [ { n: "野兽之相", e: "Aspect of the Beast" } ] },
        { lv: 14, items: [ { n: "图腾调谐", e: "Totemic Attunement" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["bard"] = {
  levels: [
    { lv: 1, items: [ { n: "施法", e: "Spellcasting" }, { n: "吟游激励（d6）", e: "Bardic Inspiration (d6)" } ] },
    { lv: 2, items: [ { n: "万事通", e: "Jack of All Trades" }, { n: "休憩之歌（d6）", e: "Song of Rest (d6)" } ] },
    { lv: 3, items: [ { n: "吟游诗人学院", e: "Bard College" }, { n: "专精", e: "Expertise" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [ { n: "吟游激励（d8）", e: "Bardic Inspiration (d8)" }, { n: "灵感源泉", e: "Font of Inspiration" } ] },
    { lv: 6, items: [ { n: "反魅惑", e: "Countercharm" }, { n: "吟游诗人学院特性", e: "Bard College feature" } ] },
    { lv: 7, items: [] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [ { n: "休憩之歌（d8）", e: "Song of Rest (d8)" } ] },
    { lv: 10, items: [ { n: "吟游激励（d10）", e: "Bardic Inspiration (d10)" }, { n: "专精", e: "Expertise" } ] },
    { lv: 11, items: [] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [ { n: "休憩之歌（d10）", e: "Song of Rest (d10)" } ] },
    { lv: 14, items: [ { n: "魔法秘辛", e: "Magical Secrets" }, { n: "吟游诗人学院特性", e: "Bard College feature" } ] },
    { lv: 15, items: [ { n: "吟游激励（d12）", e: "Bardic Inspiration (d12)" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "休憩之歌（d12）", e: "Song of Rest (d12)" } ] },
    { lv: 18, items: [ { n: "魔法秘辛", e: "Magical Secrets" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "超凡灵感", e: "Superior Inspiration" } ] }
  ],
  subclasses: [
    {
      name: "博识学院", en: "College of Lore",
      features: [
        { lv: 3, items: [ { n: "额外熟练", e: "Bonus Proficiencies" }, { n: "尖刻言语", e: "Cutting Words" } ] },
        { lv: 6, items: [ { n: "额外魔法秘辛", e: "Additional Magical Secrets" } ] },
        { lv: 14, items: [ { n: "无匹技艺", e: "Peerless Skill" } ] }
      ]
    },
    {
      name: "勇气学院", en: "College of Valor",
      features: [
        { lv: 3, items: [ { n: "额外熟练", e: "Bonus Proficiencies" }, { n: "战斗激励", e: "Combat Inspiration" } ] },
        { lv: 6, items: [ { n: "额外攻击", e: "Extra Attack" } ] },
        { lv: 14, items: [ { n: "战斗魔法", e: "Battle Magic" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["cleric"] = {
  levels: [
    { lv: 1, items: [ { n: "施法", e: "Spellcasting" }, { n: "神圣领域", e: "Divine Domain" } ] },
    { lv: 2, items: [ { n: "引导神力（驱散不死）", e: "Channel Divinity (Turn Undead)" }, { n: "神圣领域特性", e: "Divine Domain feature" } ] },
    { lv: 3, items: [] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [ { n: "摧毁不死（CR 1/2）", e: "Destroy Undead (CR 1/2)" } ] },
    { lv: 6, items: [ { n: "引导神力（2次/休息）", e: "Channel Divinity (2/rest)" }, { n: "神圣领域特性", e: "Divine Domain feature" } ] },
    { lv: 7, items: [] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" }, { n: "摧毁不死（CR 1）", e: "Destroy Undead (CR 1)" }, { n: "神圣领域特性", e: "Divine Domain feature" } ] },
    { lv: 9, items: [] },
    { lv: 10, items: [ { n: "神圣干预", e: "Divine Intervention" } ] },
    { lv: 11, items: [ { n: "摧毁不死（CR 2）", e: "Destroy Undead (CR 2)" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [] },
    { lv: 14, items: [ { n: "摧毁不死（CR 3）", e: "Destroy Undead (CR 3)" } ] },
    { lv: 15, items: [] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "摧毁不死（CR 4）", e: "Destroy Undead (CR 4)" }, { n: "神圣领域特性", e: "Divine Domain feature" } ] },
    { lv: 18, items: [ { n: "引导神力（3次/休息）", e: "Channel Divinity (3/rest)" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "神圣干预强化", e: "Divine Intervention improvement" } ] }
  ],
  subclasses: [
    {
      name: "知识领域", en: "Knowledge Domain",
      features: [
        { lv: 1, items: [ { n: "知识祝福", e: "Blessings of Knowledge" } ] },
        { lv: 2, items: [ { n: "岁月知识", e: "Knowledge of the Ages" } ] },
        { lv: 6, items: [ { n: "读心", e: "Read Thoughts" } ] },
        { lv: 8, items: [ { n: "强力施法", e: "Potent Spellcasting" } ] },
        { lv: 17, items: [ { n: "过去幻象", e: "Visions of the Past" } ] }
      ]
    },
    {
      name: "生命领域", en: "Life Domain",
      features: [
        { lv: 1, items: [ { n: "额外熟练", e: "Bonus Proficiency" }, { n: "生命门徒", e: "Disciple of Life" } ] },
        { lv: 2, items: [ { n: "保全生命", e: "Preserve Life" } ] },
        { lv: 6, items: [ { n: "受祝医者", e: "Blessed Healer" } ] },
        { lv: 8, items: [ { n: "神圣打击", e: "Divine Strike" } ] },
        { lv: 17, items: [ { n: "至善治疗", e: "Supreme Healing" } ] }
      ]
    },
    {
      name: "光明领域", en: "Light Domain",
      features: [
        { lv: 1, items: [ { n: "额外戏法", e: "Bonus Cantrip" }, { n: "守护闪光", e: "Warding Flare" } ] },
        { lv: 2, items: [ { n: "黎明光辉", e: "Radiance of the Dawn" } ] },
        { lv: 6, items: [ { n: "强化闪光", e: "Improved Flare" } ] },
        { lv: 8, items: [ { n: "强力施法", e: "Potent Spellcasting" } ] },
        { lv: 17, items: [ { n: "光明之冠", e: "Corona of Light" } ] }
      ]
    },
    {
      name: "自然领域", en: "Nature Domain",
      features: [
        { lv: 1, items: [ { n: "自然侍僧", e: "Acolyte of Nature" }, { n: "额外熟练", e: "Bonus Proficiency" } ] },
        { lv: 2, items: [ { n: "魅惑动植物", e: "Charm Animals and Plants" } ] },
        { lv: 6, items: [ { n: "压制元素", e: "Dampen Elements" } ] },
        { lv: 8, items: [ { n: "神圣打击", e: "Divine Strike" } ] },
        { lv: 17, items: [ { n: "自然大师", e: "Master of Nature" } ] }
      ]
    },
    {
      name: "风暴领域", en: "Tempest Domain",
      features: [
        { lv: 1, items: [ { n: "额外熟练", e: "Bonus Proficiencies" }, { n: "风暴之怒", e: "Wrath of the Storm" } ] },
        { lv: 2, items: [ { n: "毁灭之怒", e: "Destructive Wrath" } ] },
        { lv: 6, items: [ { n: "雷击", e: "Thunderbolt Strike" } ] },
        { lv: 8, items: [ { n: "神圣打击", e: "Divine Strike" } ] },
        { lv: 17, items: [ { n: "风暴降生", e: "Stormborn" } ] }
      ]
    },
    {
      name: "诡计领域", en: "Trickery Domain",
      features: [
        { lv: 1, items: [ { n: "诡术师祝福", e: "Blessing of the Trickster" } ] },
        { lv: 2, items: [ { n: "召唤分身", e: "Invoke Duplicity" } ] },
        { lv: 6, items: [ { n: "暗影斗篷", e: "Cloak of Shadows" } ] },
        { lv: 8, items: [ { n: "神圣打击", e: "Divine Strike" } ] },
        { lv: 17, items: [ { n: "强化分身", e: "Improved Duplicity" } ] }
      ]
    },
    {
      name: "战争领域", en: "War Domain",
      features: [
        { lv: 1, items: [ { n: "额外熟练", e: "Bonus Proficiencies" }, { n: "战争牧师", e: "War Priest" } ] },
        { lv: 2, items: [ { n: "指引打击", e: "Guided Strike" } ] },
        { lv: 6, items: [ { n: "战神祝福", e: "War God's Blessing" } ] },
        { lv: 8, items: [ { n: "神圣打击", e: "Divine Strike" } ] },
        { lv: 17, items: [ { n: "战斗化身", e: "Avatar of Battle" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["druid"] = {
  levels: [
    { lv: 1, items: [ { n: "德鲁伊语", e: "Druidic" }, { n: "施法", e: "Spellcasting" } ] },
    { lv: 2, items: [ { n: "野性形态", e: "Wild Shape" }, { n: "德鲁伊结社", e: "Druid Circle" } ] },
    { lv: 3, items: [] },
    { lv: 4, items: [ { n: "野性形态强化（CR 1/2）", e: "Wild Shape improvement (CR 1/2)" }, { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [] },
    { lv: 6, items: [ { n: "德鲁伊结社特性", e: "Druid Circle feature" } ] },
    { lv: 7, items: [] },
    { lv: 8, items: [ { n: "野性形态强化（CR 1）", e: "Wild Shape improvement (CR 1)" }, { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [] },
    { lv: 10, items: [ { n: "德鲁伊结社特性", e: "Druid Circle feature" } ] },
    { lv: 11, items: [] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [] },
    { lv: 14, items: [ { n: "德鲁伊结社特性", e: "Druid Circle feature" } ] },
    { lv: 15, items: [] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [] },
    { lv: 18, items: [ { n: "不老之躯", e: "Timeless Body" }, { n: "野兽施法", e: "Beast Spells" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "大德鲁伊", e: "Archdruid" } ] }
  ],
  subclasses: [
    {
      name: "大地结社", en: "Circle of the Land",
      features: [
        { lv: 2, items: [ { n: "额外戏法", e: "Bonus Cantrip" }, { n: "自然恢复", e: "Natural Recovery" } ] },
        { lv: 3, items: [ { n: "结社法术", e: "Circle Spells" } ] },
        { lv: 6, items: [ { n: "大地疾行", e: "Land's Stride" } ] },
        { lv: 10, items: [ { n: "自然守护", e: "Nature's Ward" } ] },
        { lv: 14, items: [ { n: "自然圣所", e: "Nature's Sanctuary" } ] }
      ]
    },
    {
      name: "月亮结社", en: "Circle of the Moon",
      features: [
        { lv: 2, items: [ { n: "战斗野性形态", e: "Combat Wild Shape" }, { n: "结社形态", e: "Circle Forms" } ] },
        { lv: 6, items: [ { n: "原始打击", e: "Primal Strike" } ] },
        { lv: 10, items: [ { n: "元素野性形态", e: "Elemental Wild Shape" } ] },
        { lv: 14, items: [ { n: "千形", e: "Thousand Forms" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["fighter"] = {
  levels: [
    { lv: 1, items: [ { n: "战斗风格", e: "Fighting Style" }, { n: "回气", e: "Second Wind" } ] },
    { lv: 2, items: [ { n: "动作如潮", e: "Action Surge" } ] },
    { lv: 3, items: [ { n: "武术范型", e: "Martial Archetype" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [ { n: "额外攻击", e: "Extra Attack" } ] },
    { lv: 6, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 7, items: [ { n: "武术范型特性", e: "Martial Archetype feature" } ] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [ { n: "不屈", e: "Indomitable" } ] },
    { lv: 10, items: [ { n: "武术范型特性", e: "Martial Archetype feature" } ] },
    { lv: 11, items: [ { n: "额外攻击（2）", e: "Extra Attack (2)" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [ { n: "不屈（2）", e: "Indomitable (2)" } ] },
    { lv: 14, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 15, items: [ { n: "武术范型特性", e: "Martial Archetype feature" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "动作如潮（2）", e: "Action Surge (2)" }, { n: "不屈（3）", e: "Indomitable (3)" } ] },
    { lv: 18, items: [ { n: "武术范型特性", e: "Martial Archetype feature" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "额外攻击（3）", e: "Extra Attack (3)" } ] }
  ],
  subclasses: [
    {
      name: "勇士", en: "Champion",
      features: [
        { lv: 3, items: [ { n: "强化重击", e: "Improved Critical" } ] },
        { lv: 7, items: [ { n: "非凡运动员", e: "Remarkable Athlete" } ] },
        { lv: 10, items: [ { n: "额外战斗风格", e: "Additional Fighting Style" } ] },
        { lv: 15, items: [ { n: "卓越重击", e: "Superior Critical" } ] },
        { lv: 18, items: [ { n: "生存者", e: "Survivor" } ] }
      ]
    },
    {
      name: "战斗大师", en: "Battle Master",
      features: [
        { lv: 3, items: [ { n: "战斗优势", e: "Combat Superiority" }, { n: "战争学徒", e: "Student of War" } ] },
        { lv: 7, items: [ { n: "知彼", e: "Know Your Enemy" } ] },
        { lv: 10, items: [ { n: "强化战斗优势（d10）", e: "Improved Combat Superiority (d10)" } ] },
        { lv: 15, items: [ { n: "不屈", e: "Relentless" } ] },
        { lv: 18, items: [ { n: "强化战斗优势（d12）", e: "Improved Combat Superiority (d12)" } ] }
      ]
    },
    {
      name: "奥法骑士", en: "Eldritch Knight",
      features: [
        { lv: 3, items: [ { n: "施法", e: "Spellcasting" }, { n: "武器联结", e: "Weapon Bond" } ] },
        { lv: 7, items: [ { n: "战斗魔法", e: "War Magic" } ] },
        { lv: 10, items: [ { n: "奥法打击", e: "Eldritch Strike" } ] },
        { lv: 15, items: [ { n: "奥术冲锋", e: "Arcane Charge" } ] },
        { lv: 18, items: [ { n: "强化战斗魔法", e: "Improved War Magic" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["monk"] = {
  levels: [
    { lv: 1, items: [ { n: "无甲防御", e: "Unarmored Defense" }, { n: "武艺", e: "Martial Arts" } ] },
    { lv: 2, items: [ { n: "气", e: "Ki" }, { n: "无甲移动", e: "Unarmored Movement" } ] },
    { lv: 3, items: [ { n: "武僧传统", e: "Monastic Tradition" }, { n: "偏转飞弹", e: "Deflect Missiles" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" }, { n: "缓落", e: "Slow Fall" } ] },
    { lv: 5, items: [ { n: "额外攻击", e: "Extra Attack" }, { n: "震慑拳", e: "Stunning Strike" } ] },
    { lv: 6, items: [ { n: "气灌打击", e: "Ki-Empowered Strikes" }, { n: "武僧传统特性", e: "Monastic Tradition feature" } ] },
    { lv: 7, items: [ { n: "闪避", e: "Evasion" }, { n: "心境止水", e: "Stillness of Mind" } ] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [ { n: "无甲移动强化", e: "Unarmored Movement improvement" } ] },
    { lv: 10, items: [ { n: "身心纯净", e: "Purity of Body" } ] },
    { lv: 11, items: [ { n: "武僧传统特性", e: "Monastic Tradition feature" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [ { n: "日月之语", e: "Tongue of the Sun and Moon" } ] },
    { lv: 14, items: [ { n: "金刚心", e: "Diamond Soul" } ] },
    { lv: 15, items: [ { n: "不老之躯", e: "Timeless Body" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "武僧传统特性", e: "Monastic Tradition feature" } ] },
    { lv: 18, items: [ { n: "空之躯", e: "Empty Body" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "完美自我", e: "Perfect Self" } ] }
  ],
  subclasses: [
    {
      name: "开手之道", en: "Way of the Open Hand",
      features: [
        { lv: 3, items: [ { n: "开手技法", e: "Open Hand Technique" } ] },
        { lv: 6, items: [ { n: "身体健全", e: "Wholeness of Body" } ] },
        { lv: 11, items: [ { n: "宁静", e: "Tranquility" } ] },
        { lv: 17, items: [ { n: "震颤掌", e: "Quivering Palm" } ] }
      ]
    },
    {
      name: "暗影之道", en: "Way of Shadow",
      features: [
        { lv: 3, items: [ { n: "暗影技艺", e: "Shadow Arts" } ] },
        { lv: 6, items: [ { n: "暗影步", e: "Shadow Step" } ] },
        { lv: 11, items: [ { n: "暗影斗篷", e: "Cloak of Shadows" } ] },
        { lv: 17, items: [ { n: "投机者", e: "Opportunist" } ] }
      ]
    },
    {
      name: "四象之道", en: "Way of the Four Elements",
      features: [
        { lv: 3, items: [ { n: "元素门徒", e: "Disciple of the Elements" }, { n: "元素法门", e: "Elemental Disciplines" } ] },
        { lv: 6, items: [ { n: "元素法门（更多）", e: "Elemental Disciplines (More)" } ] },
        { lv: 11, items: [ { n: "元素法门（更多）", e: "Elemental Disciplines (More)" } ] },
        { lv: 17, items: [ { n: "元素法门（更多）", e: "Elemental Disciplines (More)" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["paladin"] = {
  levels: [
    { lv: 1, items: [ { n: "神圣感知", e: "Divine Sense" }, { n: "圣疗", e: "Lay on Hands" } ] },
    { lv: 2, items: [ { n: "战斗风格", e: "Fighting Style" }, { n: "施法", e: "Spellcasting" }, { n: "神圣破斩", e: "Divine Smite" } ] },
    { lv: 3, items: [ { n: "神圣健康", e: "Divine Health" }, { n: "神圣誓言", e: "Sacred Oath" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [ { n: "额外攻击", e: "Extra Attack" } ] },
    { lv: 6, items: [ { n: "守护灵光", e: "Aura of Protection" } ] },
    { lv: 7, items: [ { n: "神圣誓言特性", e: "Sacred Oath feature" } ] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [] },
    { lv: 10, items: [ { n: "勇气灵光", e: "Aura of Courage" } ] },
    { lv: 11, items: [ { n: "强化神圣破斩", e: "Improved Divine Smite" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [] },
    { lv: 14, items: [ { n: "净化之触", e: "Cleansing Touch" } ] },
    { lv: 15, items: [ { n: "神圣誓言特性", e: "Sacred Oath feature" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [] },
    { lv: 18, items: [ { n: "灵光范围强化", e: "Aura improvements" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "神圣誓言特性", e: "Sacred Oath feature" } ] }
  ],
  subclasses: [
    {
      name: "奉献之誓", en: "Oath of Devotion",
      features: [
        { lv: 3, items: [ { n: "神圣武器", e: "Sacred Weapon" }, { n: "驱散邪物", e: "Turn the Unholy" } ] },
        { lv: 7, items: [ { n: "奉献灵光", e: "Aura of Devotion" } ] },
        { lv: 15, items: [ { n: "灵魂纯净", e: "Purity of Spirit" } ] },
        { lv: 20, items: [ { n: "神圣光环", e: "Holy Nimbus" } ] }
      ]
    },
    {
      name: "上古之誓", en: "Oath of the Ancients",
      features: [
        { lv: 3, items: [ { n: "自然之怒", e: "Nature's Wrath" }, { n: "驱散无信者", e: "Turn the Faithless" } ] },
        { lv: 7, items: [ { n: "守护灵光", e: "Aura of Warding" } ] },
        { lv: 15, items: [ { n: "不灭哨兵", e: "Undying Sentinel" } ] },
        { lv: 20, items: [ { n: "上古勇士", e: "Elder Champion" } ] }
      ]
    },
    {
      name: "复仇之誓", en: "Oath of Vengeance",
      features: [
        { lv: 3, items: [ { n: "弃绝敌人", e: "Abjure Enemy" }, { n: "敌誓", e: "Vow of Enmity" } ] },
        { lv: 7, items: [ { n: "不屈复仇者", e: "Relentless Avenger" } ] },
        { lv: 15, items: [ { n: "复仇之魂", e: "Soul of Vengeance" } ] },
        { lv: 20, items: [ { n: "复仇天使", e: "Avenging Angel" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["ranger"] = {
  levels: [
    { lv: 1, items: [ { n: "宿敌", e: "Favored Enemy" }, { n: "自然探索者", e: "Natural Explorer" } ] },
    { lv: 2, items: [ { n: "战斗风格", e: "Fighting Style" }, { n: "施法", e: "Spellcasting" } ] },
    { lv: 3, items: [ { n: "原始感知", e: "Primeval Awareness" }, { n: "游侠范型", e: "Ranger Archetype" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [ { n: "额外攻击", e: "Extra Attack" } ] },
    { lv: 6, items: [ { n: "宿敌与自然探索者强化", e: "Favored Enemy and Natural Explorer improvements" } ] },
    { lv: 7, items: [ { n: "游侠范型特性", e: "Ranger Archetype feature" } ] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" }, { n: "大地疾行", e: "Land's Stride" } ] },
    { lv: 9, items: [] },
    { lv: 10, items: [ { n: "自然探索者强化", e: "Natural Explorer improvement" }, { n: "林间隐匿", e: "Hide in Plain Sight" } ] },
    { lv: 11, items: [ { n: "游侠范型特性", e: "Ranger Archetype feature" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [] },
    { lv: 14, items: [ { n: "宿敌强化", e: "Favored Enemy improvement" }, { n: "消失", e: "Vanish" } ] },
    { lv: 15, items: [ { n: "游侠范型特性", e: "Ranger Archetype feature" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [] },
    { lv: 18, items: [ { n: "野性感知", e: "Feral Senses" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "屠戮者", e: "Foe Slayer" } ] }
  ],
  subclasses: [
    {
      name: "猎人", en: "Hunter",
      features: [
        { lv: 3, items: [ { n: "猎人猎物", e: "Hunter's Prey" } ] },
        { lv: 7, items: [ { n: "防御战术", e: "Defensive Tactics" } ] },
        { lv: 11, items: [ { n: "多重攻击", e: "Multiattack" } ] },
        { lv: 15, items: [ { n: "卓越猎手防御", e: "Superior Hunter's Defense" } ] }
      ]
    },
    {
      name: "驯兽师", en: "Beast Master",
      features: [
        { lv: 3, items: [ { n: "游侠伙伴", e: "Ranger's Companion" } ] },
        { lv: 7, items: [ { n: "卓越训练", e: "Exceptional Training" } ] },
        { lv: 11, items: [ { n: "野兽之怒", e: "Bestial Fury" } ] },
        { lv: 15, items: [ { n: "共享法术", e: "Share Spells" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["rogue"] = {
  levels: [
    { lv: 1, items: [ { n: "专精", e: "Expertise" }, { n: "偷袭", e: "Sneak Attack" }, { n: "盗贼黑话", e: "Thieves' Cant" } ] },
    { lv: 2, items: [ { n: "灵巧动作", e: "Cunning Action" } ] },
    { lv: 3, items: [ { n: "游荡者范型", e: "Roguish Archetype" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [ { n: "闪避", e: "Uncanny Dodge" } ] },
    { lv: 6, items: [ { n: "专精", e: "Expertise" } ] },
    { lv: 7, items: [ { n: "闪避", e: "Evasion" } ] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [ { n: "游荡者范型特性", e: "Roguish Archetype feature" } ] },
    { lv: 10, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 11, items: [ { n: "可靠才能", e: "Reliable Talent" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [ { n: "游荡者范型特性", e: "Roguish Archetype feature" } ] },
    { lv: 14, items: [ { n: "盲感", e: "Blindsense" } ] },
    { lv: 15, items: [ { n: "机敏心智", e: "Slippery Mind" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "游荡者范型特性", e: "Roguish Archetype feature" } ] },
    { lv: 18, items: [ { n: "飘忽不定", e: "Elusive" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "幸运一击", e: "Stroke of Luck" } ] }
  ],
  subclasses: [
    {
      name: "盗贼", en: "Thief",
      features: [
        { lv: 3, items: [ { n: "快手", e: "Fast Hands" }, { n: "飞檐走壁", e: "Second-Story Work" } ] },
        { lv: 9, items: [ { n: "超凡潜行", e: "Supreme Sneak" } ] },
        { lv: 13, items: [ { n: "使用魔法装置", e: "Use Magic Device" } ] },
        { lv: 17, items: [ { n: "盗贼反射", e: "Thief's Reflexes" } ] }
      ]
    },
    {
      name: "刺客", en: "Assassin",
      features: [
        { lv: 3, items: [ { n: "额外熟练", e: "Bonus Proficiencies" }, { n: "暗杀", e: "Assassinate" } ] },
        { lv: 9, items: [ { n: "渗透专家", e: "Infiltration Expertise" } ] },
        { lv: 13, items: [ { n: "伪装大师", e: "Impostor" } ] },
        { lv: 17, items: [ { n: "死亡打击", e: "Death Strike" } ] }
      ]
    },
    {
      name: "奥法诡术师", en: "Arcane Trickster",
      features: [
        { lv: 3, items: [ { n: "施法", e: "Spellcasting" }, { n: "法师之手巧技", e: "Mage Hand Legerdemain" } ] },
        { lv: 9, items: [ { n: "魔法伏击", e: "Magical Ambush" } ] },
        { lv: 13, items: [ { n: "多面诡术师", e: "Versatile Trickster" } ] },
        { lv: 17, items: [ { n: "法术窃取", e: "Spell Thief" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["sorcerer"] = {
  levels: [
    { lv: 1, items: [ { n: "施法", e: "Spellcasting" }, { n: "术法起源", e: "Sorcerous Origin" } ] },
    { lv: 2, items: [ { n: "魔法源泉", e: "Font of Magic" } ] },
    { lv: 3, items: [ { n: "超魔法", e: "Metamagic" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [] },
    { lv: 6, items: [ { n: "术法起源特性", e: "Sorcerous Origin feature" } ] },
    { lv: 7, items: [] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [] },
    { lv: 10, items: [ { n: "超魔法", e: "Metamagic" } ] },
    { lv: 11, items: [] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [] },
    { lv: 14, items: [ { n: "术法起源特性", e: "Sorcerous Origin feature" } ] },
    { lv: 15, items: [] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "超魔法", e: "Metamagic" } ] },
    { lv: 18, items: [ { n: "术法起源特性", e: "Sorcerous Origin feature" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "术法恢复", e: "Sorcerous Restoration" } ] }
  ],
  subclasses: [
    {
      name: "龙族血脉", en: "Draconic Bloodline",
      features: [
        { lv: 1, items: [ { n: "龙族先祖", e: "Dragon Ancestor" }, { n: "龙族韧性", e: "Draconic Resilience" } ] },
        { lv: 6, items: [ { n: "元素亲和", e: "Elemental Affinity" } ] },
        { lv: 14, items: [ { n: "龙翼", e: "Dragon Wings" } ] },
        { lv: 18, items: [ { n: "龙之威仪", e: "Draconic Presence" } ] }
      ]
    },
    {
      name: "狂野魔法", en: "Wild Magic",
      features: [
        { lv: 1, items: [ { n: "狂野魔法涌动", e: "Wild Magic Surge" }, { n: "混沌之潮", e: "Tides of Chaos" } ] },
        { lv: 6, items: [ { n: "扭曲幸运", e: "Bend Luck" } ] },
        { lv: 14, items: [ { n: "受控混沌", e: "Controlled Chaos" } ] },
        { lv: 18, items: [ { n: "法术轰击", e: "Spell Bombardment" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["warlock"] = {
  levels: [
    { lv: 1, items: [ { n: "异界宗主", e: "Otherworldly Patron" }, { n: "契约魔法", e: "Pact Magic" } ] },
    { lv: 2, items: [ { n: "魔能祈唤", e: "Eldritch Invocations" } ] },
    { lv: 3, items: [ { n: "契约恩赐", e: "Pact Boon" } ] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [] },
    { lv: 6, items: [ { n: "异界宗主特性", e: "Otherworldly Patron feature" } ] },
    { lv: 7, items: [] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [] },
    { lv: 10, items: [ { n: "异界宗主特性", e: "Otherworldly Patron feature" } ] },
    { lv: 11, items: [ { n: "秘法奥秘（6环）", e: "Mystic Arcanum (6th level)" } ] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [ { n: "秘法奥秘（7环）", e: "Mystic Arcanum (7th level)" } ] },
    { lv: 14, items: [ { n: "异界宗主特性", e: "Otherworldly Patron feature" } ] },
    { lv: 15, items: [ { n: "秘法奥秘（8环）", e: "Mystic Arcanum (8th level)" } ] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [ { n: "秘法奥秘（9环）", e: "Mystic Arcanum (9th level)" } ] },
    { lv: 18, items: [] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "魔能大师", e: "Eldritch Master" } ] }
  ],
  subclasses: [
    {
      name: "妖精宗主", en: "The Archfey",
      features: [
        { lv: 1, items: [ { n: "妖精显灵", e: "Fey Presence" } ] },
        { lv: 6, items: [ { n: "迷雾脱逃", e: "Misty Escape" } ] },
        { lv: 10, items: [ { n: "迷惑防御", e: "Beguiling Defenses" } ] },
        { lv: 14, items: [ { n: "黑暗谵妄", e: "Dark Delirium" } ] }
      ]
    },
    {
      name: "邪魔宗主", en: "The Fiend",
      features: [
        { lv: 1, items: [ { n: "黑暗祝福", e: "Dark One's Blessing" } ] },
        { lv: 6, items: [ { n: "黑暗幸运", e: "Dark One's Own Luck" } ] },
        { lv: 10, items: [ { n: "邪魔韧性", e: "Fiendish Resilience" } ] },
        { lv: 14, items: [ { n: "掷入地狱", e: "Hurl Through Hell" } ] }
      ]
    },
    {
      name: "旧日支配者", en: "The Great Old One",
      features: [
        { lv: 1, items: [ { n: "觉醒心灵", e: "Awakened Mind" } ] },
        { lv: 6, items: [ { n: "熵之守护", e: "Entropic Ward" } ] },
        { lv: 10, items: [ { n: "思想护盾", e: "Thought Shield" } ] },
        { lv: 14, items: [ { n: "创造奴仆", e: "Create Thrall" } ] }
      ]
    }
  ]
};

CLASS_FEATURES["wizard"] = {
  levels: [
    { lv: 1, items: [ { n: "施法", e: "Spellcasting" }, { n: "奥术恢复", e: "Arcane Recovery" } ] },
    { lv: 2, items: [ { n: "奥术传统", e: "Arcane Tradition" } ] },
    { lv: 3, items: [] },
    { lv: 4, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 5, items: [] },
    { lv: 6, items: [ { n: "奥术传统特性", e: "Arcane Tradition feature" } ] },
    { lv: 7, items: [] },
    { lv: 8, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 9, items: [] },
    { lv: 10, items: [ { n: "奥术传统特性", e: "Arcane Tradition feature" } ] },
    { lv: 11, items: [] },
    { lv: 12, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 13, items: [] },
    { lv: 14, items: [ { n: "奥术传统特性", e: "Arcane Tradition feature" } ] },
    { lv: 15, items: [] },
    { lv: 16, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 17, items: [] },
    { lv: 18, items: [ { n: "法术精通", e: "Spell Mastery" } ] },
    { lv: 19, items: [ { n: "属性值提升", e: "Ability Score Improvement" } ] },
    { lv: 20, items: [ { n: "招牌法术", e: "Signature Spells" } ] }
  ],
  subclasses: [
    {
      name: "防护学派", en: "School of Abjuration",
      features: [
        { lv: 2, items: [ { n: "防护学者", e: "Abjuration Savant" }, { n: "奥术守御", e: "Arcane Ward" } ] },
        { lv: 6, items: [ { n: "投射守御", e: "Projected Ward" } ] },
        { lv: 10, items: [ { n: "强化防护", e: "Improved Abjuration" } ] },
        { lv: 14, items: [ { n: "法术抗性", e: "Spell Resistance" } ] }
      ]
    },
    {
      name: "咒法学派", en: "School of Conjuration",
      features: [
        { lv: 2, items: [ { n: "咒法学者", e: "Conjuration Savant" }, { n: "小型咒法", e: "Minor Conjuration" } ] },
        { lv: 6, items: [ { n: "良性瞬移", e: "Benign Transposition" } ] },
        { lv: 10, items: [ { n: "专注咒法", e: "Focused Conjuration" } ] },
        { lv: 14, items: [ { n: "持久召唤", e: "Durable Summons" } ] }
      ]
    },
    {
      name: "预言学派", en: "School of Divination",
      features: [
        { lv: 2, items: [ { n: "预言学者", e: "Divination Savant" }, { n: "预兆", e: "Portent" } ] },
        { lv: 6, items: [ { n: "专家预言", e: "Expert Divination" } ] },
        { lv: 10, items: [ { n: "第三只眼", e: "The Third Eye" } ] },
        { lv: 14, items: [ { n: "高等预兆", e: "Greater Portent" } ] }
      ]
    },
    {
      name: "附魔学派", en: "School of Enchantment",
      features: [
        { lv: 2, items: [ { n: "附魔学者", e: "Enchantment Savant" }, { n: "催眠凝视", e: "Hypnotic Gaze" } ] },
        { lv: 6, items: [ { n: "本能魅惑", e: "Instinctive Charm" } ] },
        { lv: 10, items: [ { n: "分裂附魔", e: "Split Enchantment" } ] },
        { lv: 14, items: [ { n: "篡改记忆", e: "Alter Memories" } ] }
      ]
    },
    {
      name: "塑能学派", en: "School of Evocation",
      features: [
        { lv: 2, items: [ { n: "塑能学者", e: "Evocation Savant" }, { n: "塑形法术", e: "Sculpt Spells" } ] },
        { lv: 6, items: [ { n: "强力戏法", e: "Potent Cantrip" } ] },
        { lv: 10, items: [ { n: "强化塑能", e: "Empowered Evocation" } ] },
        { lv: 14, items: [ { n: "超载", e: "Overchannel" } ] }
      ]
    },
    {
      name: "幻术学派", en: "School of Illusion",
      features: [
        { lv: 2, items: [ { n: "幻术学者", e: "Illusion Savant" }, { n: "强化次级幻影", e: "Improved Minor Illusion" } ] },
        { lv: 6, items: [ { n: "可塑幻术", e: "Malleable Illusions" } ] },
        { lv: 10, items: [ { n: "幻影自我", e: "Illusory Self" } ] },
        { lv: 14, items: [ { n: "虚幻真实", e: "Illusory Reality" } ] }
      ]
    },
    {
      name: "死灵学派", en: "School of Necromancy",
      features: [
        { lv: 2, items: [ { n: "死灵学者", e: "Necromancy Savant" }, { n: "恐怖收割", e: "Grim Harvest" } ] },
        { lv: 6, items: [ { n: "亡灵仆从", e: "Undead Thralls" } ] },
        { lv: 10, items: [ { n: "不死适应", e: "Inured to Undeath" } ] },
        { lv: 14, items: [ { n: "命令亡灵", e: "Command Undead" } ] }
      ]
    },
    {
      name: "变化学派", en: "School of Transmutation",
      features: [
        { lv: 2, items: [ { n: "变化学者", e: "Transmutation Savant" }, { n: "小型炼金", e: "Minor Alchemy" } ] },
        { lv: 6, items: [ { n: "变化师之石", e: "Transmuter's Stone" } ] },
        { lv: 10, items: [ { n: "变形者", e: "Shapechanger" } ] },
        { lv: 14, items: [ { n: "变化大师", e: "Master Transmuter" } ] }
      ]
    }
  ]
};

/* ------------------------------------------------------------------ page logic */

var BUDGET = 27;
var MIN_SCORE = 8;
var MAX_SCORE = 15;
var POINT_COST = { 8: 0, 9: 1, 10: 2, 11: 3, 12: 4, 13: 5, 14: 7, 15: 9 };


var ABILITIES = [
  { key: "str" }, { key: "dex" }, { key: "con" },
  { key: "int" }, { key: "wis" }, { key: "cha" }
];

var TABS = [
  "screen.dndclasses.creation.tab.overview",
  "screen.dndclasses.creation.tab.levels",
  "screen.dndclasses.creation.tab.subclass",
  "screen.dndclasses.creation.tab.equipment"
];

/* 子职元数据：该职业 子职选择等级 + 子职 ID 有序表。
 * 显示名由 id 的 path 生成键 subclass.dndclasses.<path>.name。
 * 只有选择等级 <= 1 的职业可在创建界面选择，其余仅作预览。 */
var SUBCLASS_META = {
  barbarian: { level: 3, ids: ["dndclasses:berserker", "dndclasses:totem_warrior"] },
  bard: { level: 3, ids: ["dndclasses:lore", "dndclasses:valor"] },
  cleric: { level: 1, ids: [
    "dndclasses:knowledge", "dndclasses:life", "dndclasses:light", "dndclasses:nature",
    "dndclasses:tempest", "dndclasses:trickery", "dndclasses:war"] },
  druid: { level: 2, ids: ["dndclasses:land", "dndclasses:moon"] },
  fighter: { level: 3, ids: ["dndclasses:champion", "dndclasses:battle_master", "dndclasses:eldritch_knight"] },
  monk: { level: 3, ids: ["dndclasses:open_hand", "dndclasses:shadow", "dndclasses:four_elements"] },
  paladin: { level: 3, ids: ["dndclasses:devotion", "dndclasses:ancients", "dndclasses:vengeance"] },
  ranger: { level: 3, ids: ["dndclasses:hunter", "dndclasses:beast_master"] },
  rogue: { level: 3, ids: ["dndclasses:thief", "dndclasses:assassin", "dndclasses:arcane_trickster"] },
  sorcerer: { level: 1, ids: ["dndclasses:draconic_bloodline", "dndclasses:wild_magic"] },
  warlock: { level: 1, ids: ["dndclasses:archfey", "dndclasses:fiend", "dndclasses:great_old_one"] },
  wizard: { level: 2, ids: [
    "dndclasses:abjuration", "dndclasses:conjuration", "dndclasses:divination", "dndclasses:enchantment",
    "dndclasses:evocation", "dndclasses:illusion", "dndclasses:necromancy", "dndclasses:transmutation"] }
};

function subclassMeta(c) {
  return SUBCLASS_META[c.key] || { level: 3, ids: [] };
}

function subclassNameKey(c, index) {
  var meta = subclassMeta(c);
  if (!meta.ids || index < 0 || index >= meta.ids.length) return "";
  var id = String(meta.ids[index]);
  var path = id.indexOf(":") >= 0 ? id.split(":")[1] : id;
  return "subclass.dndclasses." + path + ".name";
}

function itemName(id) {
  var s = String(id);
  var ns = "minecraft", path = s;
  if (s.indexOf(":") >= 0) { ns = s.split(":")[0]; path = s.split(":")[1]; }
  return t("item." + ns + "." + path);
}

function eq(items) { return { items: items }; }

function armorSet(prefix) {
  return [prefix + "_helmet", prefix + "_chestplate", prefix + "_leggings", prefix + "_boots"];
}


var CLASSES = [
  {
    key: "barbarian", hitDie: "D12",
    primaryKey: "class.dndclasses.barbarian.primary", primaryAbilities: ["str"],
    saves: ["str", "con"],
    armor: ["light", "medium", "shield"], weapons: ["martial", "simple"],
    equipment: [
      eq(["dndclasses:iron_greataxe", "dndclasses:iron_handaxe", "dndclasses:iron_handaxe"]),
      eq(["dndclasses:iron_longsword"])
    ]
  },
  {
    key: "bard", hitDie: "D8",
    primaryKey: "class.dndclasses.bard.primary", primaryAbilities: ["cha"],
    saves: ["dex", "cha"],
    armor: ["light"], weapons: ["hand_crossbow", "longsword", "rapier", "shortsword", "simple"],
    equipment: [
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_rapier", "dndclasses:iron_dagger"])),
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_longsword", "dndclasses:iron_dagger"]))
    ]
  },
  {
    key: "cleric", hitDie: "D8",
    primaryKey: "class.dndclasses.cleric.primary", primaryAbilities: ["wis"],
    saves: ["wis", "cha"],
    armor: ["light", "medium", "shield"], weapons: ["simple"],
    equipment: [
      eq(armorSet("dndclasses:iron_scale_mail").concat(["dndclasses:iron_mace", "minecraft:shield"])),
      eq(armorSet("dndclasses:iron_scale_mail").concat(["dndclasses:iron_warhammer", "minecraft:shield"]))
    ]
  },
  {
    key: "druid", hitDie: "D8",
    primaryKey: "class.dndclasses.druid.primary", primaryAbilities: ["wis"],
    saves: ["int", "wis"],
    armor: ["light", "medium", "shield_nonmetal"], weapons: ["simple", "scimitar", "quarterstaff", "sling"],
    equipment: [
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_quarterstaff"])),
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_scimitar", "minecraft:shield"]))
    ]
  },
  {
    key: "fighter", hitDie: "D10",
    primaryKey: "class.dndclasses.fighter.primary", primaryAbilities: ["str", "dex"],
    saves: ["str", "con"],
    armor: ["all", "shield"], weapons: ["martial", "simple"],
    equipment: [
      eq(armorSet("dndclasses:iron_chain_mail").concat(["minecraft:shield", "dndclasses:iron_longsword"])),
      eq(armorSet("dndclasses:iron_chain_mail").concat(["dndclasses:iron_greatsword"]))
    ]
  },
  {
    key: "monk", hitDie: "D8",
    primaryKey: "class.dndclasses.monk.primary", primaryAbilities: ["dex", "wis"],
    saves: ["str", "dex"],
    armor: ["none"], weapons: ["simple", "shortsword"],
    equipment: [
      eq(["dndclasses:iron_shortsword", "dndclasses:iron_dagger"]),
      eq(["dndclasses:iron_quarterstaff"])
    ]
  },
  {
    key: "paladin", hitDie: "D10",
    primaryKey: "class.dndclasses.paladin.primary", primaryAbilities: ["str", "cha"],
    saves: ["wis", "cha"],
    armor: ["all", "shield"], weapons: ["martial", "simple"],
    equipment: [
      eq(armorSet("dndclasses:iron_chain_mail").concat(["minecraft:shield", "dndclasses:iron_longsword"])),
      eq(armorSet("dndclasses:iron_plate").concat(["dndclasses:iron_greatsword"]))
    ]
  },
  {
    key: "ranger", hitDie: "D10",
    primaryKey: "class.dndclasses.ranger.primary", primaryAbilities: ["dex", "wis"],
    saves: ["str", "dex"],
    armor: ["light", "medium", "shield"], weapons: ["martial", "simple"],
    equipment: [
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_shortsword", "dndclasses:iron_longbow", "dndclasses:iron_arrow"])),
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_shortsword", "dndclasses:iron_shortsword"]))
    ]
  },
  {
    key: "rogue", hitDie: "D8",
    primaryKey: "class.dndclasses.rogue.primary", primaryAbilities: ["dex"],
    saves: ["dex", "int"],
    armor: ["light"], weapons: ["hand_crossbow", "longsword", "rapier", "shortsword", "simple"],
    equipment: [
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_rapier", "dndclasses:iron_shortbow", "dndclasses:iron_arrow"])),
      eq(armorSet("dndclasses:iron_leather").concat(["dndclasses:iron_shortsword", "dndclasses:iron_shortsword"]))
    ]
  },
  {
    key: "sorcerer", hitDie: "D6",
    primaryKey: "class.dndclasses.sorcerer.primary", primaryAbilities: ["cha"],
    saves: ["con", "cha"],
    armor: ["none"], weapons: ["dagger", "sling", "quarterstaff", "light_crossbow"],
    equipment: [
      eq(["dndclasses:iron_light_crossbow", "dndclasses:iron_dagger", "dndclasses:iron_bolt"]),
      eq(["dndclasses:iron_quarterstaff", "dndclasses:iron_dagger"])
    ]
  },
  {
    key: "warlock", hitDie: "D8",
    primaryKey: "class.dndclasses.warlock.primary", primaryAbilities: ["cha"],
    saves: ["wis", "cha"],
    armor: ["light"], weapons: ["simple"],
    equipment: [
      eq(["dndclasses:iron_light_crossbow", "dndclasses:iron_dagger", "dndclasses:iron_bolt"]),
      eq(["dndclasses:iron_quarterstaff", "dndclasses:iron_dagger"])
    ]
  },
  {
    key: "wizard", hitDie: "D6",
    primaryKey: "class.dndclasses.wizard.primary", primaryAbilities: ["int"],
    saves: ["int", "wis"],
    armor: ["none"], weapons: ["dagger", "sling", "quarterstaff", "light_crossbow"],
    equipment: [
      eq(["dndclasses:iron_quarterstaff", "dndclasses:iron_dagger"]),
      eq(["dndclasses:iron_light_crossbow", "dndclasses:iron_dagger", "dndclasses:iron_bolt"])
    ]
  }
];

/* 语言中立的职业图标：内联 SVG，颜色继承 .seal 的 color（金色）。
 * 竖/横笔画用 rect 而非 line：AUI 的 Svg 渲染会跳过零面积的退化笔画。 */
function svg(inner) {
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
    + 'stroke-linecap="round" stroke-linejoin="round">' + inner + '</svg>';
}

function bar(x, y, w, h) {
  return '<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + h
    + '" fill="currentColor" stroke="none"/>';
}

var SEALS = {
  barbarian: svg(bar(12, 4, 2, 17) + '<polygon points="13,4 21,7 20,13 13,11"/>'),
  bard: svg('<circle cx="7" cy="18" r="3"/><circle cx="17" cy="16" r="3"/>'
    + bar(9, 6, 2, 12) + bar(19, 5, 2, 11) + '<line x1="10" y1="6" x2="20" y2="5"/>'),
  cleric: svg(bar(11, 3, 2, 18) + bar(6, 8, 12, 2)),
  druid: svg('<polygon points="12,3 19,10 12,21 5,10"/>' + bar(11, 8, 2, 13)),
  fighter: svg(bar(11, 5, 2, 16) + bar(7, 9, 10, 2) + '<polygon points="12,2 14,6 10,6"/>'),
  monk: svg('<rect x="9" y="10" width="6" height="9"/>'
    + bar(8, 6, 2, 4) + bar(11, 5, 2, 5) + bar(14, 6, 2, 4) + bar(6, 12, 3, 2)),
  paladin: svg('<polygon points="12,3 20,6 20,12 12,21 4,12 4,6"/>'
    + bar(11, 7, 2, 8) + bar(8, 9, 8, 2)),
  ranger: svg('<line x1="5" y1="19" x2="19" y2="5"/><polygon points="19,5 12,5 19,12"/>'
    + '<line x1="11" y1="13" x2="7" y2="17"/>'),
  rogue: svg('<polygon points="12,3 15,10 9,10"/>' + bar(11, 10, 2, 8) + bar(9, 17, 6, 2)),
  sorcerer: svg('<polygon points="12,2 14,10 22,12 14,14 12,22 10,14 2,12 10,10"/>'),
  warlock: svg('<ellipse cx="12" cy="12" rx="9" ry="5"/><circle cx="12" cy="12" r="2.5"/>'),
  wizard: svg('<polygon points="12,3 18,16 6,16"/>' + bar(4, 15, 16, 2) + '<circle cx="12" cy="11" r="1"/>')
};

var state = {
  selected: null,
  equipment: 0,
  tab: 0,
  subclass: 0,
  subclassChosen: null,
  abilities: { str: 8, dex: 8, con: 8, int: 8, wis: 8, cha: 8 },
  raceBonuses: { str: 0, dex: 0, con: 0, int: 0, wis: 0, cha: 0 }
};

// AUI's `window` is a Java object and cannot hold JS properties, so shared state lives in plain
// script globals instead.
var DND_RACE = null;

function racePoints() {
  return DND_RACE ? (DND_RACE.points || 0) : 0;
}

function raceSpent() {
  var total = 0;
  for (var key in state.raceBonuses) {
    total += state.raceBonuses[key] || 0;
  }
  return total;
}

function byId(id) { return document.getElementById(id); }

/* ---- i18n helpers --------------------------------------------------------
 * Text is rendered through AUI's <translation> element, which resolves the key
 * against the client language. There is no parameter interpolation, so any
 * "label + value" text is built as a translated label plus a plain value. */
function t(key) { return "<translation>" + key + "</translation>"; }

function mk(tag, cls, key) {
  var e = document.createElement(tag);
  if (cls) e.setAttribute("class", cls);
  if (key !== undefined && key !== null && key !== "") e.innerHTML = t(key);
  return e;
}

function abilName(key) { return t("ability.dndclasses." + key); }


function make(tag, cls, text) {
  var e = document.createElement(tag);
  if (cls) e.setAttribute("class", cls);
  if (text !== undefined && text !== null) e.textContent = String(text);
  return e;
}

function clearNode(node) {
  var kids = node.getChildren();
  for (var i = kids.size() - 1; i >= 0; i--) {
    node.removeChild(kids.get(i));
  }
}

function findClass(key) {
  for (var i = 0; i < CLASSES.length; i++) {
    if (CLASSES[i].key === key) return CLASSES[i];
  }
  return null;
}

function itemTexture(id) {
  var parts = String(id).split(":");
  var ns = parts.length > 1 ? parts[0] : "minecraft";
  var path = parts.length > 1 ? parts[1] : parts[0];
  return ns + ":textures/item/" + path + ".png";
}

function profBonus(level) {
  return 2 + Math.floor((level - 1) / 4);
}

function scoreCost(score) {
  return POINT_COST[score] === undefined ? -1 : POINT_COST[score];
}

function totalCost() {
  var sum = 0;
  for (var i = 0; i < ABILITIES.length; i++) {
    sum += scoreCost(state.abilities[ABILITIES[i].key]);
  }
  return sum;
}

function describeAbilities() {
  var parts = [];
  for (var i = 0; i < ABILITIES.length; i++) {
    var a = ABILITIES[i];
    var sc = state.abilities[a.key];
    var mod = Math.floor((sc - 10) / 2);
    parts.push(a.key + " " + sc + "(" + (mod >= 0 ? "+" : "") + mod + ")");
  }
  return parts.join("  ");
}

/* ------------------------------------------------------------- rendering */

function renderClassList() {
  var list = byId("class-list");
  if (!list) return;
  clearNode(list);
  for (var i = 0; i < CLASSES.length; i++) {
    var c = CLASSES[i];
    var active = state.selected === c.key ? " active" : "";
    var item = make("div", "class-item" + active);
    var seal = make("span", "seal");
    seal.innerHTML = SEALS[c.key] || "";
    var info = make("span", "class-info");
    info.appendChild(mk("span", "class-name", "class.dndclasses." + c.key));
    info.appendChild(mk("span", "class-en", "class.dndclasses." + c.key + ".subtitle"));
    var die = make("span", "class-die", c.hitDie);
    item.appendChild(seal);
    item.appendChild(info);
    item.appendChild(die);
    bindClassItem(item, c.key);
    list.appendChild(item);
  }
}

function bindClassItem(item, key) {
  item.addEventListener("click", function () { selectClass(key); });
}

function selectClass(key) {
  state.selected = key;
  state.equipment = 0;
  state.tab = 0;
  state.subclass = 0;
  state.subclassChosen = null;
  renderClassList();
  renderDetail();
  renderAbility();
  updateFooterSummary();
  refreshConfirmState();
  setMsg("", false);
}

function sectionLabel(key) {
  return mk("div", "section-label", key);
}

function runeRow(items) {
  var row = make("div", "rune-row");
  for (var i = 0; i < items.length; i++) {
    row.appendChild(mk("span", "rune", items[i]));
  }
  return row;
}

function statBox(labelKey, value) {
  var box = make("div", "stat");
  box.appendChild(mk("div", "stat-label", labelKey));
  if (value && typeof value === "object" && value.nodeType) box.appendChild(value);
  else box.appendChild(make("div", "stat-value", value));
  return box;
}

function renderDetail() {
  var header = byId("detail-header");
  var body = byId("detail-body");
  if (!header || !body) return;
  clearNode(body);
  if (!state.selected) {
    header.innerHTML = t("screen.dndclasses.creation.detail_title");
    var empty = mk("div", "empty-state", "screen.dndclasses.creation.detail_empty");
    body.appendChild(empty);
    return;
  }

  var c = findClass(state.selected);
  header.innerHTML = t("class.dndclasses." + c.key) + t("class.dndclasses." + c.key + ".subtitle_inline");

  var hero = make("div", "detail-hero");
  var heroSeal = make("span", "seal seal-lg");
  heroSeal.innerHTML = SEALS[c.key] || "";
  hero.appendChild(heroSeal);
  var heroText = make("div", null, null);
  heroText.appendChild(mk("div", "detail-name", "class.dndclasses." + c.key));
  var enLine = make("div", "detail-en", null);
  enLine.innerHTML = t("class.dndclasses." + c.key + ".subtitle");
  heroText.appendChild(enLine);
  hero.appendChild(heroText);
  body.appendChild(hero);

  body.appendChild(mk("p", "detail-desc", "class.dndclasses." + c.key + ".desc"));

  var stats = make("div", "stat-grid");
  stats.appendChild(statBox("screen.dndclasses.creation.hit_die", c.hitDie));
  stats.appendChild(statBox("screen.dndclasses.creation.primary", mk("div", "stat-value", c.primaryKey)));
  stats.appendChild(statBox("screen.dndclasses.creation.saves", runeRow(c.saves)));
  body.appendChild(stats);

  var tabsBar = make("div", "tabs detail-tabs");
  for (var t = 0; t < TABS.length; t++) {
    bindTabButton(tabsBar, t, TABS[t]);
  }
  body.appendChild(tabsBar);

  body.appendChild(renderActivePane(c));
}

function bindTabButton(bar, index, labelKey) {
  var active = state.tab === index ? " active" : "";
  var btn = mk("button", "tab" + active, labelKey);
  btn.setAttribute("type", "button");
  btn.addEventListener("click", function () { state.tab = index; renderDetail(); });
  bar.appendChild(btn);
}

function renderActivePane(c) {
  if (state.tab === 0) return paneOverview(c);
  if (state.tab === 1) return paneLevels(c);
  if (state.tab === 2) return paneSubclasses(c);
  return paneEquipment(c);
}

function prefixKeys(prefix, arr) {
  var out = [];
  for (var i = 0; i < arr.length; i++) out.push(prefix + arr[i]);
  return out;
}

function paneOverview(c) {
  var pane = make("div", "tab-pane");
  pane.appendChild(sectionLabel("screen.dndclasses.creation.armor_prof"));
  pane.appendChild(runeRow(prefixKeys("prof.dndclasses.", c.armor)));
  pane.appendChild(sectionLabel("screen.dndclasses.creation.weapon_prof"));
  pane.appendChild(runeRow(prefixKeys("prof.dndclasses.", c.weapons)));
  pane.appendChild(sectionLabel("screen.dndclasses.creation.saves"));
  pane.appendChild(runeRow(prefixKeys("ability.dndclasses.", c.saves)));
  return pane;
}

function slugify(value) {
  return String(value)
    .toLowerCase()
    .replace(/'/g, "")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function featureBlock(item) {
  var box = make("div", "feature");
  var key = "feature.dndclasses." + slugify(item.e || item.n);
  var title = make("div", "feature-title");
  var name = make("span", "feature-name", null);
  name.innerHTML = t(key) + t(key + ".subtitle");
  title.appendChild(name);
  box.appendChild(title);
  var text = make("div", "feature-text", null);
  if (item.t) text.textContent = item.t;
  else text.innerHTML = t("feature.dndclasses.desc_placeholder");
  box.appendChild(text);
  return box;
}

function levelRow(level, items) {
  var row = make("div", "level-row" + (items.length ? "" : " empty"));
  var head = make("div", "level-head");
  head.appendChild(make("span", "level-badge", "Lv " + level));
  var prof = make("span", "level-prof", null);
  prof.innerHTML = t("screen.dndclasses.creation.level_prof") + profBonus(level);
  head.appendChild(prof);
  row.appendChild(head);
  var list = make("div", "level-items");
  if (!items.length) {
    list.appendChild(make("div", "level-none", "-"));
  } else {
    for (var i = 0; i < items.length; i++) {
      list.appendChild(featureBlock(items[i]));
    }
  }
  row.appendChild(list);
  return row;
}

function itemsAtLevel(levels, level) {
  if (!levels) return [];
  for (var i = 0; i < levels.length; i++) {
    if (levels[i].lv === level) return levels[i].items;
  }
  return [];
}

function paneLevels(c) {
  var pane = make("div", "tab-pane");
  var data = CLASS_FEATURES[c.key];
  if (!data) {
    pane.appendChild(mk("div", "empty-state", "screen.dndclasses.creation.no_level_data"));
    return pane;
  }
  if (data.intro) {
    pane.appendChild(sectionLabel("screen.dndclasses.creation.class_intro"));
    pane.appendChild(make("div", "level-intro", data.intro));
  }
  pane.appendChild(sectionLabel("screen.dndclasses.creation.level_features"));
  for (var lv = 1; lv <= 20; lv++) {
    pane.appendChild(levelRow(lv, itemsAtLevel(data.levels, lv)));
  }
  return pane;
}

function paneSubclasses(c) {
  var pane = make("div", "tab-pane");
  var data = CLASS_FEATURES[c.key];
  var meta = subclassMeta(c);
  if (!data || !meta.ids || meta.ids.length === 0) {
    pane.appendChild(mk("div", "empty-state", "screen.dndclasses.creation.no_subclass_data"));
    return pane;
  }
  var selectable = meta.level <= 1;

  var hint = make("div", "subclass-hint", null);
  if (selectable) {
    hint.innerHTML = t("screen.dndclasses.creation.subclass_select");
  } else {
    hint.innerHTML = t("screen.dndclasses.creation.subclass_preview")
      + meta.level + t("screen.dndclasses.creation.subclass_preview_suffix");
  }
  pane.appendChild(hint);

  var bar = make("div", "subclass-list");
  for (var i = 0; i < meta.ids.length; i++) {
    bindSubclassButton(bar, i, subclassNameKey(c, i), selectable);
  }
  pane.appendChild(bar);

  var count = data.subclasses ? data.subclasses.length : 0;
  var idx = state.subclass < count ? state.subclass : 0;
  var sc = data.subclasses ? data.subclasses[idx] : null;
  pane.appendChild(mk("div", "subclass-intro", subclassNameKey(c, idx)));
  if (sc && sc.intro) {
    pane.appendChild(make("div", "level-intro", sc.intro));
  }
  if (sc) {
    for (var lv = 1; lv <= 20; lv++) {
      var items = itemsAtLevel(sc.features, lv);
      if (!items.length) continue;
      pane.appendChild(levelRow(lv, items));
    }
  }
  return pane;
}

function bindSubclassButton(bar, index, labelKey, selectable) {
  var active = state.subclass === index ? " active" : "";
  var chosen = selectable && state.subclassChosen === index ? " chosen" : "";
  var btn = mk("button", "subclass-tab" + active + chosen, labelKey);
  btn.setAttribute("type", "button");
  btn.addEventListener("click", function () {
    state.subclass = index;
    if (selectable) state.subclassChosen = index;
    renderDetail();
  });
  bar.appendChild(btn);
}

function chosenSubclassId(c) {
  var meta = subclassMeta(c);
  if (meta.level > 1 || state.subclassChosen === null) return "";
  if (!meta.ids || state.subclassChosen >= meta.ids.length) return "";
  return meta.ids[state.subclassChosen];
}

function paneEquipment(c) {
  var pane = make("div", "tab-pane");
  pane.appendChild(sectionLabel("screen.dndclasses.creation.choose_equipment"));
  for (var i = 0; i < c.equipment.length; i++) {
    bindEquipChoice(pane, c, i);
  }
  return pane;
}

function bindEquipChoice(pane, c, idx) {
  var choice = c.equipment[idx];
  var active = state.equipment === idx ? " active" : "";
  var row = make("div", "equip-choice" + active);
  row.appendChild(make("span", "equip-radio"));
  var body = make("div", "equip-body");
  body.appendChild(mk("div", "equip-label", "equip.dndclasses." + c.key + "." + idx));
  var items = make("div", "item-row");
  for (var j = 0; j < choice.items.length; j++) {
    var id = choice.items[j];
    var chip = make("div", "item-chip");
    chip.appendChild(make("div", "item-placeholder"));
    var nameSpan = make("span", "item-name", null);
    nameSpan.innerHTML = itemName(id);
    chip.appendChild(nameSpan);
    items.appendChild(chip);
  }
  body.appendChild(items);
  row.appendChild(body);
  row.addEventListener("click", function () { state.equipment = idx; renderDetail(); });
  pane.appendChild(row);
}

function renderAbility() {
  var body = byId("ability-body");
  if (!body) return;
  clearNode(body);
  var spent = totalCost();
  var remaining = BUDGET - spent;
  var c = state.selected ? findClass(state.selected) : null;
  var primary = c ? c.primaryAbilities : [];

  var line = make("div", "points-line");
  line.appendChild(mk("span", "points-label", "screen.dndclasses.creation.points_spent"));
  line.appendChild(make("span", "points-value", spent + " / " + BUDGET));
  body.appendChild(line);

  var progress = make("div", "progress");
  var bar = make("div", "progress-bar");
  bar.setAttribute("style", "width:" + Math.round(spent * 100 / BUDGET) + "%");
  progress.appendChild(bar);
  body.appendChild(progress);

  var costRow = make("div", "cost-row rune-row");
  var remainRune = make("span", "rune", null);
  remainRune.innerHTML = t("screen.dndclasses.creation.points_remaining") + remaining;
  costRow.appendChild(remainRune);
  costRow.appendChild(mk("span", "rune", "screen.dndclasses.creation.points_range"));
  body.appendChild(costRow);

  for (var i = 0; i < ABILITIES.length; i++) {
    bindAbilityRow(body, ABILITIES[i], remaining, primary);
  }

  body.appendChild(mk("div", "ability-caption", "screen.dndclasses.creation.ability_caption"));
}

function bindAbilityRow(body, a, remaining, primary) {
  var sc = state.abilities[a.key];
  var bonus = (DND_RACE && DND_RACE.bonuses) ? (DND_RACE.bonuses[a.key] || 0) : 0;
  var finalScore = Math.min(20, sc + bonus);
  var mod = Math.floor((finalScore - 10) / 2);
  var canPlus = sc < MAX_SCORE && remaining >= (scoreCost(sc + 1) - scoreCost(sc));
  var canMinus = sc > MIN_SCORE;
  var isPrimary = primary.indexOf(a.key) >= 0;

  var row = make("div", "ability-row" + (isPrimary ? " primary" : ""));
  var minus = make("button", "pip-btn", "-");
  minus.setAttribute("type", "button");
  if (!canMinus) minus.setAttribute("disabled", "disabled");
  var score = make("span", "ability-score", bonus
    ? String(finalScore) + " (" + (bonus > 0 ? "+" : "") + bonus + ")"
    : String(finalScore));
  var plus = make("button", "pip-btn", "+");
  plus.setAttribute("type", "button");
  if (!canPlus) plus.setAttribute("disabled", "disabled");
  row.appendChild(minus);
  row.appendChild(score);
  row.appendChild(plus);

  var abName = make("span", "ability-name", null);
  abName.innerHTML = abilName(a.key);
  row.appendChild(abName);
  row.appendChild(make("span", "ability-mod", (mod >= 0 ? "+" : "") + mod));

  minus.addEventListener("click", function () { changeAbility(a.key, -1); });
  plus.addEventListener("click", function () { changeAbility(a.key, 1); });
  body.appendChild(row);
}

function changeAbility(key, delta) {
  var sc = state.abilities[key];
  if (delta > 0) {
    if (sc >= MAX_SCORE) return;
    if (totalCost() + (scoreCost(sc + 1) - scoreCost(sc)) > BUDGET) return;
    state.abilities[key] = sc + 1;
  } else {
    if (sc <= MIN_SCORE) return;
    state.abilities[key] = sc - 1;
  }
  renderAbility();
  updateFooterSummary();
  refreshConfirmState();
  setMsg("", false);
}

/* --------------------------------------------------------------- helpers */

function updateFooterSummary() {
  var el = byId("footer-summary");
  if (!el) return;
  if (!state.selected) {
    el.innerHTML = t("screen.dndclasses.creation.footer_no_class");
    return;
  }
  var c = findClass(state.selected);
  var remaining = BUDGET - totalCost();
  el.innerHTML = t("screen.dndclasses.creation.footer_selected") + t("class.dndclasses." + c.key)
    + " (" + c.hitDie + ")  " + t("screen.dndclasses.creation.footer_remaining") + remaining;
}

function refreshConfirmState() {
  var btn = byId("btn-confirm");
  if (!btn) return;
  var raceChosen = DND_RACE;
  var ok = state.selected !== null && totalCost() === BUDGET && raceChosen;
  if (ok) btn.removeAttribute("disabled");
  else btn.setAttribute("disabled", "disabled");
}

function setMsg(key, ok) {
  var el = byId("confirm-msg");
  if (!el) return;
  el.innerHTML = key ? t(key) : "";
  if (ok) el.setAttribute("class", "confirm-msg ok");
  else el.setAttribute("class", "confirm-msg");
}

/* ---------------------------------------------------------------- modal */

function onConfirm() {
  if (!DND_RACE) {
    setMsg("screen.dndclasses.race.required", false);
    return;
  }
  if (!state.selected) { setMsg("screen.dndclasses.creation.msg_no_class", false); return; }
  if (totalCost() !== BUDGET) { setMsg("screen.dndclasses.creation.msg_budget", false); return; }
  setMsg("", false);
  openSummary();
}

function summaryLine(keyKey, valueMarkup) {
  var line = make("div", "summary-line");
  line.appendChild(mk("span", "summary-key", keyKey));
  var val = make("span", "summary-val", null);
  val.innerHTML = valueMarkup;
  line.appendChild(val);
  return line;
}

function findAbility(key) {
  for (var i = 0; i < ABILITIES.length; i++) {
    if (ABILITIES[i].key === key) return ABILITIES[i];
  }
  return null;
}

function summaryAbilities() {
  var order = [["str", "con", "dex"], ["wis", "int", "cha"]];
  var wrap = make("div", "summary-abilities");
  for (var r = 0; r < order.length; r++) {
    var row = make("div", "summary-ability-row");
    for (var i = 0; i < order[r].length; i++) {
      var ab = findAbility(order[r][i]);
      var raceBonus = (DND_RACE && DND_RACE.bonuses) ? (DND_RACE.bonuses[ab.key] || 0) : 0;
      var sc = Math.min(20, state.abilities[ab.key] + raceBonus);
      var mod = Math.floor((sc - 10) / 2);
      var cell = make("div", "summary-ability");
      cell.appendChild(mk("div", "summary-ability-name", "ability.dndclasses." + ab.key));
      cell.appendChild(make("div", "summary-ability-score", sc + " (" + (mod >= 0 ? "+" : "") + mod + ")"));
      row.appendChild(cell);
    }
    wrap.appendChild(row);
  }
  return wrap;
}

function openSummary() {
  var c = findClass(state.selected);
  var choice = c.equipment[state.equipment];
  var body = byId("sheet-body");
  if (!body) return;
  clearNode(body);
  body.appendChild(summaryLine("screen.dndclasses.creation.summary_class",
    t("class.dndclasses." + c.key) + t("class.dndclasses." + c.key + ".subtitle_inline")));
  body.appendChild(summaryLine("screen.dndclasses.creation.hit_die", c.hitDie));
  body.appendChild(summaryLine("screen.dndclasses.creation.primary", t(c.primaryKey)));
  body.appendChild(summaryLine("screen.dndclasses.creation.saves",
    prefixKeys("ability.dndclasses.", c.saves).map(function (k) { return t(k); }).join(" / ")));
  var subId = chosenSubclassId(c);
  if (subId) {
    body.appendChild(summaryLine("screen.dndclasses.creation.summary_subclass", subclassNameKey(c, state.subclassChosen)));
  }
  body.appendChild(mk("div", "summary-key summary-abilities-label", "screen.dndclasses.creation.abilities"));
  body.appendChild(summaryAbilities());
  body.appendChild(summaryLine("screen.dndclasses.creation.summary_equipment",
    t("equip.dndclasses." + c.key + "." + state.equipment)));
  var overlay = byId("sheet-overlay");
  if (overlay) overlay.setAttribute("class", "sheet-overlay open");
}

function closeSummary() {
  var overlay = byId("sheet-overlay");
  if (overlay) overlay.setAttribute("class", "sheet-overlay");
}

function commitCharacter() {
  var c = findClass(state.selected);
  var choice = c.equipment[state.equipment];
  var race = DND_RACE;
  var payload = {
    race: race ? race.race : "",
    subrace: race ? race.subrace : "",
    raceBonuses: (race && race.bonuses) ? race.bonuses : {},
    class: state.selected,
    className: c.key,
    subclass: chosenSubclassId(c),
    abilities: state.abilities,
    pointsSpent: totalCost(),
    equipmentChoice: state.equipment,
    equipmentLabel: "equip.dndclasses." + c.key + "." + state.equipment,
    equipment: choice.items
  };

  var payloadInput = byId("class-payload");
  if (payloadInput) payloadInput.value = JSON.stringify(payload);

  if (typeof console !== "undefined" && console.log) console.log("[dndClasses] character payload " + JSON.stringify(payload));
  closeSummary();
  setMsg("screen.dndclasses.creation.msg_submitted", true);
}

/* ------------------------------------------------------------- sheet mode */

var pageMode = "";
var lastStateValue = "";

function setHidden(el, hidden) {
  if (!el) return;
  var cls = el.getAttribute("class") || "";
  if (hidden) {
    if (cls.indexOf("hidden") < 0) el.setAttribute("class", cls + " hidden");
  } else {
    var parts = cls.split(" ");
    var out = "";
    for (var i = 0; i < parts.length; i++) {
      if (parts[i] && parts[i] !== "hidden") out += (out ? " " : "") + parts[i];
    }
    el.setAttribute("class", out);
  }
}

function subclassIndexByKey(c, key) {
  if (!key) return -1;
  var meta = subclassMeta(c);
  if (!meta.ids) return -1;
  for (var i = 0; i < meta.ids.length; i++) {
    if (meta.ids[i] === key) return i;
  }
  return -1;
}

function subclassNameByKey(c, key) {
  var idx = subclassIndexByKey(c, key);
  return idx >= 0 ? subclassNameKey(c, idx) : "";
}

function findSubclassByKey(c, key) {
  var idx = subclassIndexByKey(c, key);
  var data = CLASS_FEATURES[c.key];
  if (idx < 0 || !data || !data.subclasses || idx >= data.subclasses.length) return null;
  return data.subclasses[idx];
}

function sheetAbilities(abilities) {
  var order = [["str", "con", "dex"], ["wis", "int", "cha"]];
  var wrap = make("div", "summary-abilities");
  for (var r = 0; r < order.length; r++) {
    var row = make("div", "summary-ability-row");
    for (var i = 0; i < order[r].length; i++) {
      var ab = findAbility(order[r][i]);
      var sc = abilities && abilities[ab.key] !== undefined ? abilities[ab.key] : 8;
      var mod = Math.floor((sc - 10) / 2);
      var cell = make("div", "summary-ability");
      cell.appendChild(mk("div", "summary-ability-name", "ability.dndclasses." + ab.key));
      cell.appendChild(make("div", "summary-ability-score", sc + " (" + (mod >= 0 ? "+" : "") + mod + ")"));
      row.appendChild(cell);
    }
    wrap.appendChild(row);
  }
  return wrap;
}

function expBlock(data) {
  var level = data.level ? data.level : 1;
  var exp = data.exp ? data.exp : 0;
  var start = data.expLevelStart ? data.expLevelStart : 0;
  var next = data.expToNext ? data.expToNext : 0;
  var wrap = make("div", "exp-block");

  var line = make("div", "exp-line");
  line.appendChild(mk("span", "exp-label", "screen.dndclasses.sheet.experience"));

  var bar = make("div", "progress-bar");
  if (level >= 20) {
    var maxed = make("span", "exp-value", null);
    maxed.innerHTML = exp + " / " + t("screen.dndclasses.sheet.max_level");
    line.appendChild(maxed);
    bar.setAttribute("style", "width:100%");
  } else {
    line.appendChild(make("span", "exp-value", exp + " / " + next));
    var span = next - start;
    var into = exp - start;
    var pct = span > 0 ? Math.round(into * 100 / span) : 0;
    if (pct < 0) pct = 0;
    if (pct > 100) pct = 100;
    bar.setAttribute("style", "width:" + pct + "%");
  }

  var progress = make("div", "progress");
  progress.appendChild(bar);
  wrap.appendChild(line);
  wrap.appendChild(progress);
  return wrap;
}

function collectFeatures(c, level, subclassKey) {
  var groups = [];
  var data = CLASS_FEATURES[c.key];
  if (!data) return groups;
  var sub = findSubclassByKey(c, subclassKey);
  for (var lv = 1; lv <= level; lv++) {
    var items = itemsAtLevel(data.levels, lv).slice();
    if (sub) {
      var subItems = itemsAtLevel(sub.features, lv);
      for (var i = 0; i < subItems.length; i++) items.push(subItems[i]);
    }
    if (items.length) groups.push({ lv: lv, items: items });
  }
  return groups;
}

function sheetNum(v) {
  return (v === undefined || v === null) ? 0 : Math.round(v);
}

function sheetResources(data) {
  var grid = make("div", "stat-grid");
  grid.appendChild(statBox("screen.dndclasses.sheet.hp", sheetNum(data.hp) + " / " + sheetNum(data.hpMax)));
  grid.appendChild(statBox("screen.dndclasses.sheet.ac", String(data.ac === undefined ? 0 : data.ac)));
  grid.appendChild(statBox("screen.dndclasses.sheet.carry",
    String(data.weightCapacity === undefined ? 0 : data.weightCapacity)));
  return grid;
}

function sheetSlots(slots) {
  var row = make("div", "rune-row");
  var any = false;
  if (slots) {
    for (var ring in slots) {
      var s = slots[ring];
      if (!s) continue;
      any = true;
      var span = make("span", "rune", null);
      span.innerHTML = t("screen.dndclasses.sheet.slot_ring") + " " + ring + ": "
        + sheetNum(s.cur) + "/" + sheetNum(s.max);
      row.appendChild(span);
    }
  }
  if (!any) row.appendChild(make("span", "rune", "-"));
  return row;
}

function renderSheet(data) {
  var body = byId("character-sheet-body");
  var header = byId("sheet-header");
  if (!body) return;
  clearNode(body);
  var c = findClass(data["class"]);
  if (header) header.innerHTML = t("screen.dndclasses.sheet.character");

  if (!c) {
    body.appendChild(mk("div", "empty-state", "screen.dndclasses.sheet.unknown_class"));
  } else {
    var hero = make("div", "detail-hero");
    var heroSeal = make("span", "seal seal-lg");
    heroSeal.innerHTML = SEALS[c.key] || "";
    hero.appendChild(heroSeal);
    var ht = make("div");
    ht.appendChild(mk("div", "detail-name", "class.dndclasses." + c.key));
    var subName = subclassNameByKey(c, data.subclass);
    var enLine = make("div", "detail-en", null);
    enLine.innerHTML = t("class.dndclasses." + c.key + ".subtitle") + (subName ? ("  |  " + t(subName)) : "");
    ht.appendChild(enLine);
    hero.appendChild(ht);
    body.appendChild(hero);

    body.appendChild(mk("p", "detail-desc", "class.dndclasses." + c.key + ".desc"));

    var stats = make("div", "stat-grid");
    stats.appendChild(statBox("screen.dndclasses.sheet.level", "Lv " + (data.level ? data.level : 1)));
    stats.appendChild(statBox("screen.dndclasses.sheet.proficiency", "+" + (data.proficiency ? data.proficiency : 2)));
    stats.appendChild(statBox("screen.dndclasses.creation.hit_die", c.hitDie));
    body.appendChild(stats);

    body.appendChild(sectionLabel("screen.dndclasses.sheet.experience"));
    body.appendChild(expBlock(data));

    body.appendChild(sectionLabel("screen.dndclasses.creation.abilities"));
    body.appendChild(sheetAbilities(data.abilities));

    body.appendChild(sectionLabel("screen.dndclasses.sheet.resources"));
    body.appendChild(sheetResources(data));
    body.appendChild(sectionLabel("screen.dndclasses.sheet.slots"));
    body.appendChild(sheetSlots(data.slots));

    body.appendChild(sectionLabel("screen.dndclasses.sheet.gained_features"));
    var groups = collectFeatures(c, data.level ? data.level : 1, data.subclass);
    if (!groups.length) {
      body.appendChild(mk("div", "empty-state", "screen.dndclasses.sheet.no_features"));
    } else {
      for (var g = 0; g < groups.length; g++) {
        body.appendChild(levelRow(groups[g].lv, groups[g].items));
      }
    }
  }

  setHidden(byId("col-left"), true);
  setHidden(byId("col-mid"), true);
  setHidden(byId("col-right"), true);
  setHidden(byId("sheet-view"), false);
  setHidden(byId("grimoire-footer"), true);
  setHidden(byId("page-loading"), true);
  setHidden(byId("sheet-asi"), !(Number(data.pendingAsi) > 0));
  setHidden(byId("sheet-subclass"), !data.subclassAvailable);
}

function enterLoading() {
  setHidden(byId("col-left"), true);
  setHidden(byId("col-mid"), true);
  setHidden(byId("col-right"), true);
  setHidden(byId("sheet-view"), true);
  setHidden(byId("grimoire-footer"), true);
  setHidden(byId("page-loading"), false);
}

function showCreateView() {
  setHidden(byId("page-loading"), true);
  setHidden(byId("sheet-view"), true);
  setHidden(byId("col-left"), false);
  setHidden(byId("col-mid"), false);
  setHidden(byId("col-right"), false);
  setHidden(byId("grimoire-footer"), false);
}

function pollPageState() {
  var el = byId("page-state");
  if (!el) return;
  var value = el.value;
  if (!value || value === lastStateValue) return;
  var data;
  try {
    data = JSON.parse(value);
  } catch (err) {
    return;
  }
  if (!data || !data.mode) return;
  lastStateValue = value;
  if (data.mode === "sheet") {
    pageMode = "sheet";
    renderSheet(data);
  } else if (!pageMode) {
    pageMode = "create";
    showCreateView();
  }
}

/* ------------------------------------------------------------ race step */

var RACE_DATA = [
  { key: "dwarf", speed: 25, size: "medium", subraces: [
    { key: "hill_dwarf", asi: { con: 2, wis: 1 }, asiChoice: 0, speed: 25,
      traits: ["darkvision_60", "dwarf_resilience", "stonecunning", "dwarf_combat_training", "tool_proficiency"] },
    { key: "mountain_dwarf", asi: { con: 2, str: 2 }, asiChoice: 0, speed: 25,
      traits: ["darkvision_60", "dwarf_resilience", "stonecunning", "dwarf_combat_training", "dwarf_armor_training"] }
  ]},
  { key: "elf", speed: 30, size: "medium", subraces: [
    { key: "high_elf", asi: { dex: 2, int: 1 }, asiChoice: 0, speed: 30,
      traits: ["darkvision_60", "keen_senses", "fey_ancestry", "trance", "elf_weapon_training", "high_elf_cantrip"] },
    { key: "wood_elf", asi: { dex: 2, wis: 1 }, asiChoice: 0, speed: 35,
      traits: ["darkvision_60", "keen_senses", "fey_ancestry", "trance", "elf_weapon_training", "fleet_of_foot", "mask_of_the_wild"] },
    { key: "dark_elf", asi: { dex: 2, cha: 1 }, asiChoice: 0, speed: 30,
      traits: ["superior_darkvision_120", "sunlight_sensitivity", "drow_magic", "keen_senses", "fey_ancestry", "trance"] }
  ]},
  { key: "halfling", speed: 25, size: "small", subraces: [
    { key: "lightfoot", asi: { dex: 2, cha: 1 }, asiChoice: 0, speed: 25,
      traits: ["lucky", "brave", "halfling_nimbleness"] },
    { key: "stout", asi: { dex: 2, con: 1 }, asiChoice: 0, speed: 25,
      traits: ["lucky", "brave", "halfling_nimbleness", "stout_resilience"] }
  ]},
  { key: "human", speed: 30, size: "medium", subraces: [
    { key: "standard_human", asi: { str: 1, dex: 1, con: 1, int: 1, wis: 1, cha: 1 }, asiChoice: 0, speed: 30, traits: [] },
    { key: "variant_human", asi: {}, asiChoice: 2, speed: 30, traits: ["skill_versatility", "feat"] }
  ]},
  { key: "dragonborn", speed: 30, size: "medium", subraces: [
    { key: "dragonborn", asi: { str: 2, cha: 1 }, asiChoice: 0, speed: 30,
      traits: ["draconic_ancestry", "breath_weapon", "damage_resistance"] }
  ]},
  { key: "gnome", speed: 25, size: "small", subraces: [
    { key: "forest_gnome", asi: { int: 2, dex: 1 }, asiChoice: 0, speed: 25,
      traits: ["darkvision_60", "gnome_cunning", "natural_illusionist"] },
    { key: "rock_gnome", asi: { int: 2, con: 1 }, asiChoice: 0, speed: 25,
      traits: ["darkvision_60", "gnome_cunning", "artificers_lore", "tinker"] }
  ]},
  { key: "half_elf", speed: 30, size: "medium", subraces: [
    { key: "half_elf", asi: { cha: 2 }, asiChoice: 2, speed: 30,
      traits: ["darkvision_60", "fey_ancestry", "skill_versatility"] }
  ]},
  { key: "half_orc", speed: 30, size: "medium", subraces: [
    { key: "half_orc", asi: { str: 2, con: 1 }, asiChoice: 0, speed: 30,
      traits: ["darkvision_60", "menacing", "relentless_endurance", "savage_attacks"] }
  ]},
  { key: "tiefling", speed: 30, size: "medium", subraces: [
    { key: "tiefling", asi: { cha: 2, int: 1 }, asiChoice: 0, speed: 30,
      traits: ["darkvision_60", "hellish_resistance", "infernal_legacy"] }
  ]}
];
(function () {
  var colors = ["black", "blue", "brass", "bronze", "copper", "gold", "green", "red", "silver", "white"];
  for (var i = 0; i < colors.length; i++) {
    RACE_DATA[4].subraces.push({
      key: colors[i], asi: { str: 2, cha: 1 }, asiChoice: 0, speed: 30,
      traits: ["draconic_ancestry", "breath_weapon", "damage_resistance", "draconic_" + colors[i]]
    });
  }
})();

var raceState = { race: null, subrace: null, picks: [] };
var raceBound = false;

function raceFind(key) {
  for (var i = 0; i < RACE_DATA.length; i++) {
    if (RACE_DATA[i].key === key) return RACE_DATA[i];
  }
  return null;
}

function raceFindSub(race, key) {
  if (!race) return null;
  for (var i = 0; i < race.subraces.length; i++) {
    if (race.subraces[i].key === key) return race.subraces[i];
  }
  return null;
}

function racePattern(sub) {
  var values = [];
  if (!sub) return values;
  var keys = Object.keys(sub.asi || {});
  for (var i = 0; i < keys.length; i++) values.push(sub.asi[keys[i]]);
  for (var j = 0; j < (sub.asiChoice || 0); j++) values.push(1);
  values.sort(function (a, b) { return b - a; });
  return values;
}

function raceResetPicks(sub) {
  var values = racePattern(sub);
  raceState.picks = [];
  for (var i = 0; i < values.length; i++) raceState.picks.push(null);
  if (values.length === 6) {
    for (var k = 0; k < ABILITIES.length; k++) raceState.picks[k] = ABILITIES[k].key;
  }
}

function raceShowStep(isRace) {
  var raceList = byId("race-list");
  var raceDetail = byId("race-detail");
  var classList = byId("class-list");
  var classDetail = byId("detail-body");
  var footer = byId("race-footer");
  var stepRace = byId("step-race");
  var stepClass = byId("step-class");
  if (!raceList || !raceDetail || !classList || !classDetail) return;
  if (isRace) {
    raceList.setAttribute("class", "race-list");
    raceDetail.setAttribute("class", "race-detail");
    classList.setAttribute("class", "class-list hidden");
    classDetail.setAttribute("class", "hidden");
    if (footer) footer.setAttribute("class", "card-footer");
    if (stepRace) stepRace.setAttribute("class", "step-tab active");
    if (stepClass) stepClass.setAttribute("class", "step-tab");
    var header = byId("detail-header");
    if (header) header.innerHTML = t("screen.dndclasses.race.title");
  } else {
    raceList.setAttribute("class", "race-list hidden");
    raceDetail.setAttribute("class", "race-detail hidden");
    classList.setAttribute("class", "class-list");
    classDetail.setAttribute("class", "");
    if (footer) footer.setAttribute("class", "card-footer hidden");
    if (stepRace) stepRace.setAttribute("class", "step-tab");
    if (stepClass) stepClass.setAttribute("class", "step-tab active");
    var classHeader = byId("detail-header");
    if (classHeader) classHeader.innerHTML = t("screen.dndclasses.creation.detail_title");
  }
}

function raceRenderList() {
  var list = byId("race-list");
  if (!list) return;
  clearNode(list);
  for (var i = 0; i < RACE_DATA.length; i++) {
    (function (race) {
      var btn = make("button", "race-item", null);
      btn.setAttribute("type", "button");
      if (raceState.race === race.key) btn.setAttribute("class", "race-item active");
      btn.innerHTML = t("race.dndclasses." + race.key);
      btn.addEventListener("click", function () {
        raceState.race = race.key;
        raceState.subrace = race.subraces[0].key;
        raceResetPicks(race.subraces[0]);
        raceRenderList();
        raceRenderDetail();
        raceRefreshConfirm();
      });
      list.appendChild(btn);
    })(RACE_DATA[i]);
  }
}

function raceRenderDetail() {
  var detail = byId("race-detail");
  if (!detail) return;
  clearNode(detail);
  var race = raceFind(raceState.race);
  if (!race) {
    detail.appendChild(mk("p", "choice-sub", "screen.dndclasses.race.select_race"));
    return;
  }
  detail.appendChild(mk("div", "card-title", "race.dndclasses." + race.key));
  var infoLine = make("div", "card-sub", null);
  infoLine.innerHTML = t("screen.dndclasses.race.speed") + " " + race.speed + " ft  |  "
    + t("size.dndclasses." + race.size);
  detail.appendChild(infoLine);

  var subRow = make("div", "subrace-row");
  for (var i = 0; i < race.subraces.length; i++) {
    (function (sub) {
      var btn = mk("button", "subrace-btn", "race.dndclasses." + race.key + "." + sub.key);
      btn.setAttribute("type", "button");
      if (raceState.subrace === sub.key) btn.setAttribute("class", "subrace-btn active");
      btn.addEventListener("click", function () {
        raceState.subrace = sub.key;
        raceResetPicks(sub);
        raceRenderDetail();
        raceRefreshConfirm();
      });
      subRow.appendChild(btn);
    })(race.subraces[i]);
  }
  detail.appendChild(subRow);

  var sub = raceFindSub(race, raceState.subrace);
  if (!sub) return;

  var values = racePattern(sub);
  var parts = [];
  for (var v = 0; v < values.length; v++) parts.push("+" + values[v]);
  var patternLine = make("div", "card-sub", null);
  patternLine.innerHTML = t("screen.dndclasses.race.bonus_pattern") + " " + parts.join(" / ");
  detail.appendChild(patternLine);

  if (values.length < 6) {
    detail.appendChild(mk("div", "card-sub", "screen.dndclasses.race.assign_hint"));
    for (var slot = 0; slot < values.length; slot++) {
      (function (slotIndex, bonusValue) {
        var row = make("div", "asi-row");
        row.appendChild(make("span", "rune", "+" + bonusValue + ":"));
        for (var a = 0; a < ABILITIES.length; a++) {
          (function (ability) {
            var selected = raceState.picks[slotIndex] === ability.key;
            var usedElsewhere = false;
            for (var p = 0; p < raceState.picks.length; p++) {
              if (p !== slotIndex && raceState.picks[p] === ability.key) usedElsewhere = true;
            }
            var btn = make("button", "asi-btn", null);
            btn.innerHTML = abilName(ability.key);
            btn.setAttribute("type", "button");
            if (selected) btn.setAttribute("class", "asi-btn active");
            if (usedElsewhere) btn.setAttribute("class", "asi-btn used");
            btn.addEventListener("click", function () {
              if (usedElsewhere) {
                var duplicateMsg = byId("race-msg");
                if (duplicateMsg) duplicateMsg.innerHTML = t("screen.dndclasses.race.duplicate");
                return;
              }
              raceState.picks[slotIndex] = ability.key;
              raceRenderDetail();
              raceRefreshConfirm();
            });
            row.appendChild(btn);
          })(ABILITIES[a]);
        }
        detail.appendChild(row);
      })(slot, values[slot]);
    }
  }

  var traits = make("div", "trait-list");
  for (var k = 0; k < sub.traits.length; k++) {
    var chip = make("span", "trait-chip", null);
    chip.innerHTML = t("trait.dndclasses." + sub.traits[k]);
    traits.appendChild(chip);
  }
  detail.appendChild(traits);
}

function raceAllPicked() {
  for (var i = 0; i < raceState.picks.length; i++) {
    if (!raceState.picks[i]) return false;
  }
  return true;
}

function raceRefreshConfirm() {
  var confirm = byId("race-confirm");
  if (!confirm) return;
  var sub = raceFindSub(raceFind(raceState.race), raceState.subrace);
  if (sub) confirm.removeAttribute("disabled");
  else confirm.setAttribute("disabled", "disabled");
}

function raceBonuses() {
  var result = {};
  var sub = raceFindSub(raceFind(raceState.race), raceState.subrace);
  var values = racePattern(sub);
  for (var i = 0; i < values.length; i++) {
    if (raceState.picks[i]) result[raceState.picks[i]] = values[i];
  }
  return result;
}

function initRace() {
  var raceConfirm = byId("race-confirm");
  var raceList = byId("race-list");
  if (!raceConfirm || !raceList) return;
  if (!raceBound) {
    raceBound = true;
    raceConfirm.addEventListener("click", function () {
      var race = raceFind(raceState.race);
      var sub = raceFindSub(race, raceState.subrace);
      if (!race || !sub) return;
      if (!raceAllPicked()) {
        var msg = byId("race-msg");
        if (msg) msg.innerHTML = t("screen.dndclasses.race.assign_incomplete");
        return;
      }
      DND_RACE = { race: race.key, subrace: sub.key, bonuses: raceBonuses() };
      raceShowStep(false);
      dndClassRefresh();
    });
    var stepRace = byId("step-race");
    var stepClass = byId("step-class");
    if (stepRace) {
      stepRace.addEventListener("click", function () { raceShowStep(true); });
    }
    if (stepClass) {
      stepClass.addEventListener("click", function () {
        if (!DND_RACE) {
          var msg = byId("confirm-msg");
          if (msg) msg.innerHTML = t("screen.dndclasses.race.required");
          return;
        }
        raceShowStep(false);
      });
    }
  }
  DND_RACE = null;
  raceState.race = null;
  raceState.subrace = null;
  raceState.picks = [];
  raceRenderList();
  raceRenderDetail();
  raceRefreshConfirm();
  raceShowStep(true);
}

/* ----------------------------------------------------------------- init */

function init() {
  var confirm = byId("btn-confirm");
  if (confirm) confirm.addEventListener("click", onConfirm);
  var cancel = byId("sheet-cancel");
  if (cancel) cancel.addEventListener("click", closeSummary);
  var commit = byId("sheet-commit");
  if (commit) commit.addEventListener("click", commitCharacter);
  var asiButton = byId("sheet-asi");
  if (asiButton) asiButton.addEventListener("click", function () {
    var action = byId("sheet-action");
    if (action) action.value = "asi";
  });
  var subclassButton = byId("sheet-subclass");
  if (subclassButton) subclassButton.addEventListener("click", function () {
    var action = byId("sheet-action");
    if (action) action.value = "subclass";
  });

  initRace();
  renderClassList();
  renderDetail();
  renderAbility();
  updateFooterSummary();
  refreshConfirmState();

  enterLoading();
  pollPageState();
  if (typeof setInterval === "function") {
    setInterval(pollPageState, 150);
  }
  if (typeof setTimeout === "function") {
    setTimeout(function () {
      if (!pageMode) {
        pageMode = "create";
        showCreateView();
      }
    }, 1500);
  }
}

var pageStarted = false;

function dndClassRefresh() {
  renderAbility();
  updateFooterSummary();
  refreshConfirmState();
}

function boot() {
  if (pageStarted) return;
  pageStarted = true;
  init();
}

document.addEventListener("DOMContentLoaded", boot);
if (document.readyState === "complete" || document.readyState === "interactive") {
  boot();
}

/* AUI 1.2.8 screen documents may not dispatch DOMContentLoaded; boot is idempotent. */
boot();
