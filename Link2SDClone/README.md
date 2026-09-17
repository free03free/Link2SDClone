# SD Link Manager (مشروع تعليمي مستوحى من فكرة Link2SD)

هذا مشروع Android Studio (Kotlin) مبني من الصفر، يحاكي **هيكلة القوائم والإعدادات**
الخاصة بتطبيق Link2SD الشهير، لكنه **لا يحتوي على أي سطر من الكود المصدري الأصلي**
لذلك التطبيق، والذي يبقى ملكية فكرية لمطوّره.

## البنية

- `MainActivity` — قائمة التطبيقات المثبتة + فلاتر (الكل / مستخدم / نظام / مرتبط / غير مرتبط)
- `StorageInfoActivity` — عرض مساحة الذاكرة الداخلية / SD / Partition الثاني
- `SettingsActivity` + `preferences.xml` — إعدادات الربط التلقائي، Dalvik Cache، الفرز، نسخ احتياطي
- `AppDetailActivity` — تفاصيل تطبيق واحد (الحجم، الإصدار، الحالة)
- `RootUtils.kt` — طبقة تنفيذ أوامر Root عبر مكتبة `libsu` (mount/symlink)
- `AppListAdapter` — عرض القائمة في RecyclerView

## ⚠️ ملاحظات مهمة قبل التشغيل الفعلي

1. **يتطلب جهازاً مروَّقاً (rooted)** فعلياً لتعمل أوامر `RootUtils` (mount/symlink).
2. **لا ينشئ Partition جديداً على SD تلقائياً** — هذا يتطلب أدوات تقسيم خارجية
   (`parted`, `gparted`) تُشغَّل عادة من recovery أو PC، وهذا الجزء متروك عمداً
   لأنه يمس بنية القرص ويختلف حسب الجهاز.
3. الأوامر الموجودة في `RootUtils.createLink` / `removeLink` هي **توضيحية مبسطة**؛
   قد تحتاج تعديل سياقات SELinux، صلاحيات `chown`/`chmod`، والتعامل مع
   `dalvik-cache` بشكل منفصل حسب إصدار أندرويد (خصوصاً أندرويد 8+ الذي غيّر
   آلية تخزين بيانات التطبيقات بشكل كبير).
4. من إصدار أندرويد 11+ فصاعداً، القيود الأمنية (Scoped Storage، SELinux المشدّد)
   تجعل هذه الطريقة أصعب بكثير أو غير ممكنة على أجهزة غير مروَّقة بعمق (custom recovery + rooted kernel).

## 🚀 رفع المشروع على GitHub والبناء المباشر

هذا المشروع جاهز ليتم رفعه كما هو على GitHub وبناؤه تلقائياً:

### 1) الرفع على GitHub
```bash
cd Link2SDClone
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/USERNAME/REPO_NAME.git
git push -u origin main
```

### 2) البناء التلقائي عبر GitHub Actions
يحتوي المشروع على ملف `.github/workflows/android-build.yml` يقوم تلقائياً بما يلي
عند كل `push` أو `pull request`:
- تثبيت JDK 17 و Android SDK
- توليد ملفات Gradle Wrapper (`gradle wrapper`) — **لهذا لم يتم رفع `gradle-wrapper.jar` الثنائي يدوياً**، بل يُنشأ تلقائياً في كل عملية بناء لتفادي رفع ملفات ثنائية غير ضرورية للمستودع
- بناء نسخة Debug من التطبيق (`assembleDebug`)
- رفع ملف الـ APK الناتج كـ **Artifact** يمكنك تحميله من تبويب **Actions** في صفحة المستودع بعد انتهاء البناء

لا حاجة لأي إعداد إضافي — بمجرد الرفع، افتح تبويب **Actions** في GitHub وستجد البناء يعمل تلقائياً.

### 3) البناء محلياً عبر Android Studio
1. افتح المجلد بالكامل في Android Studio (File > Open).
2. عند طلب Android Studio توليد/تحميل Gradle Wrapper، اسمح بذلك (أو نفّذ يدوياً إن كان لديك Gradle مثبت):
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
3. دع Gradle يزامن المكتبات (يحتاج اتصال إنترنت لتحميل `libsu` من JitPack).
4. شغّل على جهاز حقيقي مروَّق أو محاكي به صلاحيات root (مثل محاكي مع Magisk).

### 4) البناء من سطر الأوامر مباشرة (بعد توليد الـ wrapper)
```bash
./gradlew assembleDebug
```
ستجد ملف الـ APK الناتج في:
`app/build/outputs/apk/debug/app-debug.apk`
