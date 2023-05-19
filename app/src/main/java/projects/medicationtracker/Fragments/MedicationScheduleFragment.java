package projects.medicationtracker.Fragments;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;

import kotlin.Triple;
import projects.medicationtracker.Dialogs.AddAsNeededDoseDialog;
import projects.medicationtracker.Dialogs.DoseInfoDialog;
import projects.medicationtracker.Helpers.DBHelper;
import projects.medicationtracker.Helpers.TextViewUtils;
import projects.medicationtracker.Helpers.TimeFormatting;
import projects.medicationtracker.Interfaces.IDialogCloseListener;
import projects.medicationtracker.R;
import projects.medicationtracker.SimpleClasses.Medication;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MedicationScheduleFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MedicationScheduleFragment extends Fragment implements IDialogCloseListener
{
    public static final String MEDICATIONS = "medications";
    public static final String DAY_OF_WEEK = "dayOfWeek";
    public static final String DAY_IN_CURRENT_WEEK = "dayInCurrentWeek";
    public static final String DAY_NUMBER = "dayNumber";
    private View rootView;
    private int currentPoints = 0;

    private static ArrayList<Medication> meds;
    private static DBHelper db;
    private static String dayOfWeek;
    private static LocalDate dayInCurrentWeek;
    private static int dayNumber;

    /**
     * Required empty constructor
     */
    public MedicationScheduleFragment() {}

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param medications Medications to display in schedule.
     * @param day The name of the day this fragment will display
     * @param aDayThisWeek A LocalDate in the week the user is viewing
     * @param dayNum the number of the day in the week being viewed (0 Sunday - 6 Saturday)
     *
     * @return A new instance of fragment MedicationScheduleFragment.
     */
    public static MedicationScheduleFragment newInstance(
            ArrayList<Medication> medications,
            String day,
            LocalDate aDayThisWeek,
            int dayNum
    ) {
        MedicationScheduleFragment fragment = new MedicationScheduleFragment();

        Bundle bundle = new Bundle();
        bundle.putParcelable(MEDICATIONS, (Parcelable) medications);
        bundle.putString(DAY_OF_WEEK, day);
        bundle.putLong("dayInCurrentWeek", aDayThisWeek.toEpochDay());
        bundle.putInt("dayNumber", dayNum);

        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); }

    /**
     * Builds an instance of the fragment
     * @return The fragment inflated
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        setHasOptionsMenu(true); // Enable options menu for this fragment

        LocalDate thisDate;
        currentPoints = getStoredPointsValue();

        assert getArguments() != null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        {
            meds = getArguments().getParcelableArrayList(MEDICATIONS, Medication.class);
        }
        else
        {
            meds = getArguments().getParcelableArrayList(MEDICATIONS);
        }

        dayOfWeek = getArguments().getString(DAY_OF_WEEK + "_" + container.getId());
        dayInCurrentWeek = LocalDate.ofEpochDay(getArguments().getLong(DAY_IN_CURRENT_WEEK + "_" + container.getId()));
        dayNumber = getArguments().getInt(DAY_NUMBER + "_" + container.getId());

        thisDate = TimeFormatting.whenIsSunday(dayInCurrentWeek).plusDays(dayNumber);

        rootView = inflater.inflate(
                R.layout.fragment_medication_schedule,
                container,
                false
        );

        if (meds.stream().anyMatch(m -> m.getFrequency() == 0 && !m.getStartDate().toLocalDate().isAfter(thisDate)))
        {
            TextView plusAsNeeded = rootView.findViewById(R.id.plusAsNeeded);
            LinearLayout asNeededList = rootView.findViewById(R.id.asNeededList);
//            asNeededList.setBackgroundColor(Color.BLACK);
            asNeededList.setVisibility(View.VISIBLE);

            plusAsNeeded.setTag(thisDate);

            plusAsNeeded.setVisibility(View.VISIBLE);
            plusAsNeeded.setOnClickListener(v ->
            {
                AddAsNeededDoseDialog asNeededDialog = new AddAsNeededDoseDialog(
                        meds.stream().filter(
                                m -> m.getFrequency() == 0 && !m.getStartDate().toLocalDate().isAfter(thisDate)
                        ).collect(Collectors.toCollection(ArrayList::new)),
                        (LocalDate) v.getTag(),
                        db
                );
                asNeededDialog.show(getChildFragmentManager(), null);
            });
        }

        createSchedule();

        return rootView;
    }

    /**
     * Creates a list of the medications for the current given day in places them in the fragment
     */
    private void createSchedule()
    {
        LinearLayout checkBoxHolder = rootView.findViewById(R.id.medicationSchedule);
        LinearLayout asNeededList = rootView.findViewById(R.id.asNeededViews);
        TextView dayLabel = rootView.findViewById(R.id.dateLabel);
        LocalDate thisSunday = TimeFormatting.whenIsSunday(dayInCurrentWeek);
        ArrayList<RelativeLayout> scheduledMeds = new ArrayList<>();

        ArrayList<RelativeLayout> asNeededMeds = new ArrayList<>();
        db = new DBHelper(rootView.getContext());

        String dayLabelString =
                dayOfWeek + " " + TimeFormatting.localDateToString(thisSunday.plusDays(dayNumber));
        dayLabel.setText(dayLabelString);
        dayLabel.setBackgroundColor(getResources().getColor(R.color.colorActive));
        for (Medication medication : meds)
        {
            for (LocalDateTime time : medication.getTimes())
            {
                if (time.toLocalDate().isEqual(thisSunday.plusDays(dayNumber)) && !time.isBefore(medication.getStartDate()))
                {
                    RelativeLayout rl = buildCheckbox(medication, time);
                    rl.setBackgroundColor(getResources().getColor(R.color.colorActive));
                    if (medication.getFrequency() == 0)
                        asNeededMeds.add(rl);
                    else
                        scheduledMeds.add(rl);
                }
            }
        }

        if (scheduledMeds.size() == 0)
        {
            TextView textView = new TextView(rootView.getContext());
            String noMed = getString(R.string.no_meds_for_day, dayOfWeek);

            TextViewUtils.setTextViewParams(textView, noMed, checkBoxHolder);
        }
        else
        {
            sortSchedule(scheduledMeds);
            sortSchedule(asNeededMeds);

            scheduledMeds.forEach(checkBoxHolder::addView);
            asNeededMeds.forEach(asNeededList::addView);
        }
    }

    private RelativeLayout buildCheckbox(Medication medication, LocalDateTime time)
    {
        RelativeLayout rl = new RelativeLayout(rootView.getContext());
        rl.setBackgroundColor(getResources().getColor(R.color.colorActive));
        TextView thisMedication = medication.getFrequency() > 0
                ? new CheckBox(rootView.getContext()) : new TextView(rootView.getContext());
        long medId = medication.getId();
        Triple<Medication, Long, LocalDateTime> tag;
        long doseRowId = db.getDoseId(medId, TimeFormatting.localDateTimeToString(time));
        ImageButton button = new ImageButton(rootView.getContext());

        button.setBackgroundResource(R.drawable.info_fill1_wght700_grad200_opsz24);

        rl.addView(thisMedication);
        rl.addView(button);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );

        button.setLayoutParams(layoutParams);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) button.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.addRule(RelativeLayout.CENTER_VERTICAL);

        button.setOnClickListener(v ->
        {
            DoseInfoDialog doseInfo = new DoseInfoDialog(
                    db.getDoseId(medId, TimeFormatting.localDateTimeToString(time)), db, thisMedication
            );
            doseInfo.show(getChildFragmentManager(), null);
        });

        // Set Checkbox label
        String medName = medication.getName();
        String dosage;
        if (medication.getDosage() == (int) medication.getDosage())
        {
            dosage = String.format(Locale.getDefault(), "%d", (int) medication.getDosage());
        }
        else
        {
            dosage = String.valueOf(medication.getDosage());
        }

        dosage += " " + medication.getDosageUnits();

        String dosageTime = TimeFormatting.formatTimeForUser(time.getHour(), time.getMinute());

        String thisMedicationLabel = medName + " - " + dosage + " - " + dosageTime;

        thisMedication.setText(thisMedicationLabel);

        tag = new Triple<>(medication, doseRowId, time);

        thisMedication.setTag(tag);
     //  assert thisMedication instanceof CheckBox;
        //((CheckBox) thisMedication).setButtonTintList( ColorStateList.valueOf(getResources().getColor(R.color.blue)));
        if (medication.getFrequency() == 0)
        {
            thisMedication.setTextColor(Color.BLACK);

            return rl;
        }

        if (doseRowId != -1 && db.getTaken(doseRowId))
        {
           // Toast.makeText(getContext(), doseRowId+"5 Points", Toast.LENGTH_SHORT).show();

            ((CheckBox) thisMedication).setChecked(true);
        }
        ((CheckBox) thisMedication).setOnCheckedChangeListener((compoundButton, b) ->
        {
            Triple<Medication, Long, LocalDateTime> tvTag =
                    (Triple<Medication, Long, LocalDateTime>) thisMedication.getTag();
            final Long doseId = tvTag.getSecond();
            int timeBeforeDose = db.getTimeBeforeDose();

            if (LocalDateTime.now().isBefore(time.minusHours(timeBeforeDose)) && timeBeforeDose != -1)
            {
                ((CheckBox) thisMedication).setChecked(false);
                Toast.makeText(
                        rootView.getContext(),
                        getString(R.string.cannot_take_more_than_hours, timeBeforeDose),
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }


            String now = TimeFormatting.localDateTimeToString(LocalDateTime.now());

            if (doseId != -1)
            {
                db.updateDoseStatus(doseId, now, ((CheckBox) thisMedication).isChecked());
                if(((CheckBox) thisMedication).isChecked()==true) {
                    currentPoints += 5;
                    // Save the updated points value to SharedPreferences
                    savePointsValue(currentPoints);
                    Toast.makeText(getContext(), currentPoints+"", Toast.LENGTH_SHORT).show();                }
                else if(((CheckBox) thisMedication).isChecked()==false)
                {

                }
            }
            else
            {
                long id = db.addToMedicationTracker(
                        tvTag.getFirst(),
                        tvTag.getThird()
                );

                db.updateDoseStatus(
                        id,
                        TimeFormatting.localDateTimeToString(LocalDateTime.now()),
                        true
                );
                currentPoints += 5;
                // Save the updated points value to SharedPreferences
                savePointsValue(currentPoints);
                Toast.makeText(getContext(), currentPoints+"", Toast.LENGTH_SHORT).show();
            }
        });

        return rl;
    }

    private int getStoredPointsValue() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sharedPreferences.getInt("points_value", 0);
    }
    private void savePointsValue(int points) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("points_value", points);
        editor.apply();
    }



    /**
     * Sorts CheckBoxes in medication schedule.
     * @param layouts ArrayList of all relative layout in the schedule.
     */
    private void sortSchedule(ArrayList<RelativeLayout> layouts)
    {
        int count = layouts.size();

        for (int i = 1; i < count; i++)
        {
            for (int j = 0; j < (count - i); j++)
            {
                TextView child1 = (TextView) layouts.get(j).getChildAt(0);
                TextView child2 = (TextView) layouts.get(j + 1).getChildAt(0);

                Triple<Medication, Long, LocalDateTime> child1Pair =
                        (Triple<Medication, Long, LocalDateTime>) child1.getTag();
                Triple<Medication, Long, LocalDateTime> child2Pair =
                        (Triple<Medication, Long, LocalDateTime>) child2.getTag();

                LocalDateTime child1Time = child1Pair.getThird();
                LocalDateTime child2Time = child2Pair.getThird();

                if (child1Time != null && child1Time.isAfter(child2Time))
                {
                    RelativeLayout temp = layouts.get(j);

                    layouts.set(j, layouts.get(j + 1));
                    layouts.set(j + 1, temp);
                }
            }
        }
    }

    @Override
    public void handleDialogClose(Action action, Long doseId)
    {
        LinearLayout ll = rootView.findViewById(R.id.asNeededViews);

        switch (action)
        {
            case ADD:
                LinearLayout checkBoxHolder = rootView.findViewById(R.id.medicationSchedule);

                meds.forEach(m ->
                {
                    if (m.getFrequency() == 0) m.setTimes(db.getDoseFromMedicationTracker(m));
                });

                checkBoxHolder.removeAllViews();
                ll.removeAllViews();

                createSchedule();

                break;
            case DELETE:
                for (int i = 0; i < ll.getChildCount(); i++)
                {
                    RelativeLayout layoutToDelete = (RelativeLayout) ll.getChildAt(i);

                    if (((Triple<Medication, Long, LocalDateTime>) layoutToDelete.getChildAt(0).getTag()).getSecond().equals(doseId))
                    {
                        ll.removeViewAt(i);

                        break;
                    }
                }
        }
    }
}