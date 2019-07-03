package io.intelehealth.client.dataMigration;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.intelehealth.client.BuildConfig;
import io.intelehealth.client.database.dao.EncounterDAO;
import io.intelehealth.client.database.dao.ObsDAO;
import io.intelehealth.client.database.dao.PatientsDAO;
import io.intelehealth.client.database.dao.VisitsDAO;
import io.intelehealth.client.models.dto.EncounterDTO;
import io.intelehealth.client.models.dto.ObsDTO;
import io.intelehealth.client.models.dto.PatientAttributesDTO;
import io.intelehealth.client.models.dto.PatientDTO;
import io.intelehealth.client.models.dto.VisitDTO;
import io.intelehealth.client.utilities.Logger;
import io.intelehealth.client.utilities.SessionManager;
import io.intelehealth.client.utilities.StringUtils;
import io.intelehealth.client.utilities.UuidDictionary;
import io.intelehealth.client.utilities.backup.Backup;
import io.intelehealth.client.utilities.exception.DAOException;

import static io.intelehealth.client.app.AppConstants.APP_VERSION_CODE;
import static io.intelehealth.client.app.AppConstants.dbfilepath;

public class SmoothUpgrade {
    public SQLiteDatabase myDataBase;
    SessionManager sessionManager = null;
    Context context;
    Backup backup = new Backup();
    boolean dbexist = checkdatabase();
    String TAG = SmoothUpgrade.class.getSimpleName();
    public String DATABASE_NAME = "YOUR DATABASE NAME HERE";

    public SmoothUpgrade(Context context) {
        this.context = context;
        sessionManager = new SessionManager(context);
    }

    public void copyDatabase() {
        int versionCode = BuildConfig.VERSION_CODE;

        if (versionCode <= APP_VERSION_CODE) {
            try {
                backup.createFileInMemory(context, true);
            } catch (IOException e) {
                Crashlytics.getInstance().core.logException(e);

            }
        }

    }


    public void exportDB() {
        try {
            File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();

            if (sd.canWrite()) {
                Log.d("TAG", "DatabaseHandler: can write in sd");
                //Replace with YOUR_PACKAGE_NAME and YOUR_DB_NAME
                //String currentDBPath = "filepath here"+DATABASE_NAME;
                //Replace with YOUR_FOLDER_PATH and TARGET_DB_NAME in the SD card
                //String copieDBPath = DATABASE_NAME;
                File currentDB = new File(data + "/data/io.intelehealth.client/databases/", "localRecords.db");
                File copieDB = new File(dbfilepath);
                if (currentDB.exists()) {
                    Log.d("TAG", "DatabaseHandler: DB exist");
                    @SuppressWarnings("resource")
                    FileChannel src = new FileInputStream(currentDB).getChannel();
                    @SuppressWarnings("resource")
                    FileChannel dst = new FileOutputStream(copieDB).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();
                }
            }
        } catch (Exception e) {
            Crashlytics.getInstance().core.logException(e);
        }

    }

    public Boolean checkingDatabase() {

        exportDB();

        if (checkdatabase()) {
            System.out.println("Database exists");
            opendatabase();
            insertOfflineOldData();
        } else {
            System.out.println("Database doesn't exist");
//            createdatabase();
        }

        return true;
    }

    private static Object getKeyFromValue(Map hm, Object value) {
        for (Object o : hm.keySet()) {
            if (hm.get(o).equals(value)) {
                return o;
            }
        }
        return "";
    }

    public void insertOfflineOldData() {
        myDataBase.beginTransaction();
        PatientDTO patientDTO = new PatientDTO();
        VisitDTO visitDTO = new VisitDTO();
        EncounterDTO encounterDTO = new EncounterDTO();
        ObsDTO obsDTO = new ObsDTO();
        PatientsDAO patientsDAO = new PatientsDAO();
        VisitsDAO visitsDAO = new VisitsDAO();
        PatientAttributesDTO patientAttributesDTO = new PatientAttributesDTO();
        List<PatientAttributesDTO> patientAttributesDTOList = new ArrayList<>();
        Gson gson = new Gson();
        String uuid = "";
        try {
            Cursor cursor = myDataBase.rawQuery("Select * from patient where openmrs_id IS NULL OR openmrs_id =''", null);
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    patientDTO = new PatientDTO();
                    uuid = UUID.randomUUID().toString();
                    String id = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                    visitDTO = getPatientId(id);
                    if (id.equalsIgnoreCase(visitDTO.getPatientuuid())) {
                        visitDTO.setPatientuuid(uuid);
                        try {
                            visitsDAO.insertPatientToDB(visitDTO);
                        } catch (DAOException e) {
                            Crashlytics.getInstance().core.logException(e);
                        }
                    }
                    encounterDTO = getVisitId(id, visitDTO.getUuid(), visitDTO.getStartdate());

                    String encounteruuid = UUID.randomUUID().toString();

                    obsDTO = getEncounterId(id, encounteruuid);

                    encounterDTO = insertMissingEncounter(encounteruuid, obsDTO.getConceptuuid(), visitDTO.getUuid(), visitDTO.getStartdate());


                    patientDTO.setUuid(uuid);
                    patientDTO.setFirstname(cursor.getString(cursor.getColumnIndexOrThrow("first_name")));
                    patientDTO.setMiddlename(cursor.getString(cursor.getColumnIndexOrThrow("middle_name")));
                    patientDTO.setLastname(cursor.getString(cursor.getColumnIndexOrThrow("last_name")));
                    patientDTO.setDateofbirth(cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth")));
                    patientDTO.setAddress1(cursor.getString(cursor.getColumnIndexOrThrow("address1")));
                    patientDTO.setAddress2(cursor.getString(cursor.getColumnIndexOrThrow("address2")));
                    patientDTO.setCityvillage(cursor.getString(cursor.getColumnIndexOrThrow("city_village")));
                    patientDTO.setStateprovince(cursor.getString(cursor.getColumnIndexOrThrow("state_province")));
                    patientDTO.setCountry(cursor.getString(cursor.getColumnIndexOrThrow("country")));
                    patientDTO.setGender(cursor.getString(cursor.getColumnIndexOrThrow("gender")));
                    patientDTO.setDateofbirth(cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth")));
                    patientAttributesDTO = new PatientAttributesDTO();
                    patientAttributesDTO.setUuid(UUID.randomUUID().toString());
                    patientAttributesDTO.setPatientuuid(uuid);
                    patientAttributesDTO.setPersonAttributeTypeUuid(patientsDAO.getUuidForAttribute("caste"));
                    patientAttributesDTO.setValue(StringUtils.getValue(cursor.getString(cursor.getColumnIndexOrThrow("caste"))));
                    patientAttributesDTOList.add(patientAttributesDTO);

                    patientAttributesDTO = new PatientAttributesDTO();
                    patientAttributesDTO.setUuid(UUID.randomUUID().toString());
                    patientAttributesDTO.setPatientuuid(uuid);
                    patientAttributesDTO.setPersonAttributeTypeUuid(patientsDAO.getUuidForAttribute("Telephone Number"));
                    patientAttributesDTO.setValue(StringUtils.getValue(cursor.getString(cursor.getColumnIndexOrThrow("phone_number"))));
                    patientAttributesDTOList.add(patientAttributesDTO);

                    patientAttributesDTO = new PatientAttributesDTO();
                    patientAttributesDTO.setUuid(UUID.randomUUID().toString());
                    patientAttributesDTO.setPatientuuid(uuid);
                    patientAttributesDTO.setPersonAttributeTypeUuid(patientsDAO.getUuidForAttribute("Son/wife/daughter"));
                    patientAttributesDTO.setValue(StringUtils.getValue(cursor.getString(cursor.getColumnIndexOrThrow("sdw"))));
                    patientAttributesDTOList.add(patientAttributesDTO);

                    patientAttributesDTO = new PatientAttributesDTO();
                    patientAttributesDTO.setUuid(UUID.randomUUID().toString());
                    patientAttributesDTO.setPatientuuid(uuid);
                    patientAttributesDTO.setPersonAttributeTypeUuid(patientsDAO.getUuidForAttribute("occupation"));
                    patientAttributesDTO.setValue(StringUtils.getValue(cursor.getString(cursor.getColumnIndexOrThrow("occupation"))));
                    patientAttributesDTOList.add(patientAttributesDTO);

                    patientAttributesDTO = new PatientAttributesDTO();
                    patientAttributesDTO.setUuid(UUID.randomUUID().toString());
                    patientAttributesDTO.setPatientuuid(uuid);
                    patientAttributesDTO.setPersonAttributeTypeUuid(patientsDAO.getUuidForAttribute("Economic Status"));
                    patientAttributesDTO.setValue(StringUtils.getValue(cursor.getString(cursor.getColumnIndexOrThrow("economic_status"))));
                    patientAttributesDTOList.add(patientAttributesDTO);

                    patientAttributesDTO = new PatientAttributesDTO();
                    patientAttributesDTO.setUuid(UUID.randomUUID().toString());
                    patientAttributesDTO.setPatientuuid(uuid);
                    patientAttributesDTO.setPersonAttributeTypeUuid(patientsDAO.getUuidForAttribute("Education Level"));
                    patientAttributesDTO.setValue(StringUtils.getValue(cursor.getString(cursor.getColumnIndexOrThrow("education_status"))));
                    patientAttributesDTOList.add(patientAttributesDTO);
                    Logger.logD(TAG, "PatientAttribute list" + patientAttributesDTOList.size());
                    patientDTO.setPatientAttributesDTOList(patientAttributesDTOList);
                    try {
                        patientsDAO.insertPatientToDB(patientDTO, uuid);
                    } catch (DAOException e) {
                        Crashlytics.getInstance().core.logException(e);
                    }

                    cursor.moveToNext();
                    Logger.logD("ShowData->", gson.toJson(patientDTO));
                    patientAttributesDTOList.clear();

                }
            }
            if (cursor != null) {
                cursor.close();
            }
            myDataBase.setTransactionSuccessful();
        } catch (SQLiteException e) {
            Crashlytics.getInstance().core.logException(e);
        }
    }

    private VisitDTO getPatientId(String id) {
        VisitDTO visitDTO = new VisitDTO();
        try {
            Cursor cursor = myDataBase.rawQuery("Select * from visit where openmrs_visit_uuid IS NULL AND patient_id ='" + id + "'", null);
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    visitDTO.setUuid(UUID.randomUUID().toString());
                    visitDTO.setPatientuuid(cursor.getString(cursor.getColumnIndexOrThrow("patient_id")));
                    visitDTO.setStartdate(cursor.getString(cursor.getColumnIndexOrThrow("start_datetime")));
                    visitDTO.setEnddate(cursor.getString(cursor.getColumnIndexOrThrow("end_datetime")));
                    visitDTO.setVisitTypeUuid(UuidDictionary.VISIT_TELEMEDICINE);
                    visitDTO.setLocationuuid(sessionManager.getLocationUuid());
                    visitDTO.setCreatoruuid(sessionManager.getCreatorID());

                    cursor.moveToNext();
                }

            }
            if (cursor != null) {
                cursor.close();
            }

        } catch (
                SQLiteException e) {
            Crashlytics.getInstance().core.logException(e);
        }
        return visitDTO;
    }

    private EncounterDTO getVisitId(String id, String visituuid, String time) {
        EncounterDTO encounterDTO = new EncounterDTO();
        EncounterDAO encounterDAO = new EncounterDAO();
        try {
            Cursor cursor = myDataBase.rawQuery("Select * from encounter where openmrs_encounter_id IS NULL AND visit_id ='" + id + "'", null);
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    encounterDTO.setUuid(UUID.randomUUID().toString());
                    encounterDTO.setVisituuid(visituuid);
                    encounterDTO.setEncounterTypeUuid(encounterDAO.getEncounterTypeUuid(cursor.getString(cursor.getColumnIndexOrThrow("encounter_type"))));
                    encounterDTO.setEncounterTime(time);
                    encounterDTO.setProvideruuid(sessionManager.getProviderID());
                    encounterDTO.setVoided(0);
                    encounterDTO.setSyncd(false);

                    try {
                        encounterDAO.createEncountersToDB(encounterDTO);
                    } catch (DAOException e) {
                        Crashlytics.getInstance().core.logException(e);
                    }
                    cursor.moveToNext();
                }
            }
            if (cursor != null) {
                cursor.close();
            }

        } catch (
                SQLiteException e) {
            Crashlytics.getInstance().core.logException(e);
        }
        return encounterDTO;
    }

    private ObsDTO getEncounterId(String id, String encounterUuid) {
        ObsDTO ObsDTO = new ObsDTO();
        ObsDAO obsDAO = new ObsDAO();
        try {
            Cursor cursor = myDataBase.rawQuery("Select * from obs where openmrs_obs_id IS NULL AND patient_id ='" + id + "'", null);
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    ObsDTO.setUuid(UUID.randomUUID().toString());
                    ObsDTO.setConceptuuid(convertConcepttoUuid(cursor.getString(cursor.getColumnIndexOrThrow("concept_id"))));
                    ObsDTO.setValue(cursor.getString(cursor.getColumnIndexOrThrow("value")));
                    ObsDTO.setEncounteruuid(encounterUuid);
                    ObsDTO.setCreator(4);

                    try {
                        obsDAO.insertObs(ObsDTO);
                    } catch (DAOException e) {
                        Crashlytics.getInstance().core.logException(e);
                    }
                    cursor.moveToNext();
                }
            }
            if (cursor != null) {
                cursor.close();
            }

        } catch (
                SQLiteException e) {
            Crashlytics.getInstance().core.logException(e);
        }
        return ObsDTO;
    }


    private String convertConcepttoUuid(String conceptid) {
        String key = "";
        Map<String, String> map = new HashMap<String, String>();
        map.put("CURRENT_COMPLAINT", "163212");
        map.put("PHYSICAL_EXAMINATION", "163213");
        map.put("HEIGHT", "5090");
        map.put("WEIGHT", "5089");
        map.put("PULSE", "5087");
        map.put("SYSTOLIC_BP", "5085");
        map.put("DIASTOLIC_BP", "5086");
        map.put("TEMPERATURE", "5088");
        map.put("RESPIRATORY", "5242");
        map.put("SPO2", "5092");
        map.put("RHK_MEDICAL_HISTORY_BLURB", "163210");
        map.put("RHK_FAMILY_HISTORY_BLURB", "163211");
        map.put("FOLLOW_UP_VISIT", "163345");
        map.put("TELEMEDICINE_DIAGNOSIS", "163219");
        map.put("JSV_MEDICATIONS", "163202");
        map.put("MEDICAL_ADVICE", "163216");
        map.put("REQUESTED_TESTS", "163206");
        map.put("ADDITIONAL_COMMENTS", "162169");
        map.put("SON_WIFE_DAUGHTER", "163207");
        map.put("OCCUPATION", "163208");
        map.put("PATIENT_SATISFACTION", "163343");
        map.put("COMMENTS", "163344");

        key = getKeyFromValue(map, conceptid).toString();

        return conceptUuid(key);
    }

    public EncounterDTO insertMissingEncounter(String uuid, String encounterType, String visituuid, String time) {

        EncounterDTO encounterDTO = new EncounterDTO();
        EncounterDAO encounterDAO = new EncounterDAO();
        try {

            encounterDTO.setUuid(uuid);
            encounterDTO.setVisituuid(visituuid);
            encounterDTO.setEncounterTypeUuid(getEncounterTypebasedonConcept(encounterType));
            encounterDTO.setEncounterTime(time);
            encounterDTO.setSyncd(false);
            encounterDTO.setProvideruuid(sessionManager.getProviderID());
            encounterDTO.setVoided(0);
            try {
                encounterDAO.createEncountersToDB(encounterDTO);
            } catch (DAOException e) {
                Crashlytics.getInstance().core.logException(e);
            }
        } catch (Exception e) {
            Crashlytics.getInstance().core.logException(e);
        }
        return encounterDTO;
    }

    private String getEncounterTypebasedonConcept(String encountertype) {

        String encounter = "";
        ArrayList<String> arr = new ArrayList<String>();
        arr.add("5090AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        arr.add("5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        arr.add("5087AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        arr.add("5085AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        arr.add("5086AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        arr.add("5088AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        arr.add("5242AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        arr.add("5092AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");


        ArrayList<String> arr1 = new ArrayList<String>();


        if (arr.contains(encountertype)) {
            encounter = "67a71486-1a54-468f-ac3e-7091a9a79584";
        } else {
            encounter = "8d5b27bc-c2cc-11de-8d13-0010c6dffd0f";
        }

        return encounter;
    }

    private String conceptUuid(String key) {
        EncounterDAO encounterDAO = new EncounterDAO();

        return encounterDAO.getEncounterTypeUuid(key);
    }

    private boolean checkdatabase() {

        boolean checkdb = false;
        try {
            File dbfile = new File(dbfilepath);
            checkdb = dbfile.exists();
        } catch (SQLiteException e) {
            System.out.println("Database doesn't exist");
        }
        return checkdb;
    }


    public void opendatabase() throws SQLException {
        //Open the database
        String mypath = dbfilepath;
        myDataBase = SQLiteDatabase.openDatabase(mypath, null, SQLiteDatabase.OPEN_READWRITE);
    }
}