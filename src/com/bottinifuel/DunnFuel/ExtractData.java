/*
 * Created on Jul 30, 2007 by pladd
 *
 */
package com.bottinifuel.DunnFuel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bottinifuel.ADD_Cust_Import.Account;
import com.bottinifuel.ADD_Cust_Import.CustLog;
import com.bottinifuel.ADD_Cust_Import.Service;
import com.bottinifuel.ADD_Cust_Import.Tank;
import com.bottinifuel.ADD_Cust_Import.Trailer;
import com.bottinifuel.ADD_Cust_Import.Account.CType;
import com.bottinifuel.ADD_Cust_Import.Tank.FuelType;

/**
 * @author pladd
 *
 */
public class ExtractData
{
    private DunnCustomer [] AllCustomers;
    private Map<String, List<Integer>> MultiTanks = new HashMap<String, List<Integer>>();
    private List<Integer> Customers = new Vector<Integer>();

    public ExtractData(String pathToFiles) throws Exception
    {
        File dataDir = new File(pathToFiles);
        if (!dataDir.isDirectory())
            throw new Exception("Input file path is not a directory");
        
        File custDataFile = new File(dataDir.getAbsoluteFile() + File.separator + "CUST.DAT");
        FileInputStream custData = new FileInputStream(custDataFile);

        int custCount = (int)(custDataFile.length() / 512);
        AllCustomers = new DunnCustomer[custCount+1];
        int recNum = 0;
        int bytesRead = 0;

//        PrintStream tempOut = new PrintStream("..\\..\\Dunn\\UnknownByteFields.csv");
//        tempOut.println("acct,ldg_bytes,ytd_gal_bytes,balance_bytes,ldg,ytd_gal,balance");

        
        byte [] dataBuf = new byte[512];

        DecimalFormat threeDigit = new DecimalFormat("000");
        DecimalFormat fourDigit  = new DecimalFormat("0000");

        Pattern multPat = Pattern.compile("(.*)\\(\\d+\\)(.*)");
        Matcher multMatcher = multPat.matcher("");
        
        while (custData.available() > 0)
        {
            recNum++;
            bytesRead += custData.read(dataBuf);
            if (dataBuf[0] <= 10)
            {
                System.err.println("Acct#" + recNum + " skipped - invalid 1st char");
                continue;
            }
            if (dataBuf[206] == 'I')
            {
                System.err.println("Acct#" + recNum + " skipped - inactive");
                continue;
            }
            if (dataBuf[214] == 'D')
            {
                System.err.println("Acct#" + recNum + " skipped - degree day scheduling");
                continue;
            }
            if (recNum == 1)
                continue;

            String billName = new String(dataBuf, 0,25);
            String billAddr = new String(dataBuf,25,25);
            String billCSZ  = new String(dataBuf,50,28);
            String delName  = new String(dataBuf,78,25);
            String delAddr  = new String(dataBuf,103,25);
            String delCSZ   = new String(dataBuf,128,28);
            String sortCode = new String(dataBuf,188,10);

            int acctNum;
            int tankNum;
            multMatcher.reset(billName);
            if (multMatcher.matches())
            {
                String nameWithoutMult = multMatcher.group(1).trim() + multMatcher.group(2).trim();
                billName = nameWithoutMult;

                List<Integer> multi;
                if (!MultiTanks.containsKey(nameWithoutMult))
                {
                    multi = new ArrayList<Integer>(5);
                    MultiTanks.put(nameWithoutMult, multi);
                    acctNum = recNum;
                    tankNum = 1;
                    Customers.add(recNum);
                }
                else
                {
                    multi = MultiTanks.get(nameWithoutMult);
                    acctNum = multi.get(0);
                    tankNum = multi.size() + 1;
                }
                multi.add(recNum);
            }
            else
            {
                acctNum = recNum;
                tankNum = 1;
                Customers.add(recNum);
            }

            DunnCustomer currCust = AllCustomers[recNum] =
                new DunnCustomer(acctNum, tankNum, recNum);
            
            if (tankNum != 1)
            {
                DunnCustomer master = AllCustomers[acctNum];
                master.SubAccounts.add(currCust);
            }
            
            currCust.setBillingName(billName);
            currCust.setBillingAddr(billAddr);
            currCust.setBillingCityStateZip(billCSZ);
            currCust.setDeliveryName(delName);
            currCust.setDeliveryAddr(delAddr);
            currCust.setDeliveryCityStateZip(delCSZ);
            currCust.setSortCode(sortCode);

            int areaCode = ByteBuffer.wrap(dataBuf).order(ByteOrder.BIG_ENDIAN).getShort(160); 
            int telExch  = ByteBuffer.wrap(dataBuf).order(ByteOrder.BIG_ENDIAN).getShort(156);
            int telRest  = ByteBuffer.wrap(dataBuf).order(ByteOrder.BIG_ENDIAN).getShort(158);

            String telNum = "             ";
            if (areaCode != 0 || telExch != 0 || telRest != 0)
            {
                if (areaCode == 0)
                    areaCode = 845;
                if (areaCode <= 999  && areaCode >= 0 &&
                    telExch  <= 999  && telExch  >= 0 &&
                    telRest  <= 9999 && telRest  >= 0)
                {
                }
                else
                    System.err.println("Acct #" + recNum + ": Skipping invalid phone # (" +
                                       areaCode + ")" + threeDigit.format(telExch) + "-" + fourDigit.format(telRest));
            }
            currCust.setAreaCode(areaCode);
            currCust.setTelExch(telExch);
            currCust.setTelNum(telRest);

            switch (dataBuf[206])
            {
            case 'A': currCust.setDeliveryType(DunnCustomer.DelType.AUTOMATIC); break;
            case 'C': currCust.setDeliveryType(DunnCustomer.DelType.WILL_CALL); break;
            case 'I': currCust.setDeliveryType(DunnCustomer.DelType.INACTIVE);  break;
            default:
                System.err.println("Acct #" + recNum + ": Invalid delivery type " + Character.valueOf((char)dataBuf[206]));
                break;
            }

            switch (dataBuf[207])
            {
            case 'H': currCust.setHotWater(false); break;
            case 'W': currCust.setHotWater(true);  break;
            default:
                System.err.println("Acct #" + recNum + ": Invalid heat/hot water flag " + Character.valueOf((char)dataBuf[207]) +
                " - assuming heat");
                break;
            }

            int tankSize = ByteBuffer.wrap(dataBuf).order(ByteOrder.LITTLE_ENDIAN).getShort(208);
            currCust.setTankSize(tankSize);
            
            // Important dates
            currCust.setLastCharge  (dataBuf, 198);
            currCust.setLastCredit  (dataBuf, 200);
            currCust.setLastDelivery(dataBuf, 221);

            switch (dataBuf[214])
            {
            case 'K': 
                currCust.setScheduleType(DunnCustomer.SchedType.K_FACTOR);
                int dd_last = ByteBuffer.wrap(dataBuf).order(ByteOrder.LITTLE_ENDIAN).getShort(215);
                currCust.setDD_Last(dd_last);
            
                int dd_next = ByteBuffer.wrap(dataBuf).order(ByteOrder.LITTLE_ENDIAN).getShort(217);
                currCust.setDD_Next(dd_next);
            
                double kfa = (double)ByteBuffer.wrap(dataBuf).order(ByteOrder.LITTLE_ENDIAN).getShort(219) / 10.0;
                currCust.setK_Factor(kfa);
                break;

            case 'C': 
                currCust.setScheduleType(DunnCustomer.SchedType.CALENDAR);
                currCust.setNextDeliveryDate(dataBuf, 217);
                Calendar ldd = currCust.BufToCal(dataBuf, 215);
                if (!ldd.equals(currCust.getLastDelivery()))
                    System.err.println("Acct #"+ recNum + ": LDD doesn't match on calendar account");
                currCust.setDaysToNext(ByteBuffer.wrap(dataBuf).order(ByteOrder.LITTLE_ENDIAN).getShort(219));
                break;

            case 'D': currCust.setScheduleType(DunnCustomer.SchedType.DEGREE_DAY); break;
            default:
                System.err.println("Acct #" + recNum + ": Invalid schedule type " + Character.valueOf((char)dataBuf[214]));
                break;
            }
            
            switch (dataBuf[227])
            {
            case 1: 
            case 2:
            case 3:
            case 13:
            case 15:
            case 16:
                currCust.setProduct(FuelType.TWO_OIL);
                break;
            case 4:
            case 5:
            case 6:
            case 7:
            case 14:
                currCust.setProduct(FuelType.DYED_KERO);
                break;
            case 8:
                currCust.setProduct(FuelType.DYED_DIESEL);
                break;
            case 9:
                currCust.setProduct(FuelType.NOTAX_DIESEL);
                break;
            case 10:
            case 11:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                currCust.setProduct(FuelType.WINTER_BLEND);
                break;
            case 12:
                currCust.setProduct(FuelType.CLEAR_DIESEL);
                break;
            default:
                System.err.println("Acct #" + recNum + ": Invalid product type for product " + dataBuf[227]);
                break;
            }

            switch (dataBuf[227])
            {
            case 1:
            case 4:
            case 10:
            case 11:
            case 13:
            case 14:
            case 18:
            case 19:
                currCust.setType(Account.CType.RESIDENTIAL);
                break;
            case 2:
            case 3:
            case 5:
            case 6:
            case 7:
            case 8:
            case 12:
            case 16:
            case 17:
            case 20:
            case 23:
                currCust.setType(Account.CType.COMMERCIAL);
                break;
            case 9:
            case 21:
                currCust.setType(Account.CType.FARM);
                break;
            case 15:
            case 22:
                currCust.setType(Account.CType.MUNI);
                break;
            default:
                System.err.println("Acct #" + recNum + ": Invalid product type for cust type " + dataBuf[227]);
                break;
            }
            
            switch (dataBuf[227])
            {
            case 3:
            case 7:
            case 16:
            case 20:
                currCust.setExempt(true);
                break;
            }
            
            byte []lastGals = new byte[4];
            ByteBuffer.wrap(dataBuf, 223, 4).get(lastGals, 0, 4);
            byte [] ytdg = new byte[8];
            ByteBuffer.wrap(dataBuf, 228, 8).get(ytdg, 0, 8);
            byte [] bal = new byte[8];
            ByteBuffer.wrap(dataBuf, 236, 8).get(bal, 0, 8);

//            tempOut.print(recNum + ",\"");
//            for (byte b : lastGals)
//            {
//                String s = Integer.toHexString(b & 0xFF);
//                if (s.length() == 1)
//                    s = "0" + s;
//                tempOut.print(s);
//            }
//            tempOut.print("\",\"");
//            
//            for (byte b : ytdg)
//            {
//                String s = Integer.toHexString(b & 0xFF);
//                if (s.length() == 1)
//                    s = "0" + s;
//                tempOut.print(s);
//            }
//            tempOut.print("\",\"");
//
//            for (byte b : bal)
//            {
//                String s = Integer.toHexString(b & 0xFF);
//                if (s.length() == 1)
//                    s = "0" + s;
//                tempOut.print(s);
//            }
//            tempOut.println("\"");
        }
//        tempOut.close();

        File notesDataFile = new File(dataDir.getAbsoluteFile() + File.separator + "NOTES.DAT");
        FileInputStream notesData = new FileInputStream(notesDataFile);

        recNum = 0;
        bytesRead = 0;
        byte [] notesBuf = new byte[512];
        
        Calendar conversionDate = Calendar.getInstance();
        conversionDate.set(2007, 6, 24);

        Pattern ldgPat = Pattern.compile("[01][0-9]/[0123][0-9]\\s+([0-9]*\\.[0-9])(\\S*)\\s*\\S+");
        Matcher ldgMatcher = ldgPat.matcher("");

        while (notesData.available() > 0)
        {
            recNum++;

            bytesRead += notesData.read(notesBuf);
            DunnCustomer currCust = AllCustomers[recNum];
            if (currCust == null)
                continue;

            String site = new String(notesBuf, 0,64);
            currCust.setSite(site.trim());

            String note = new String(notesBuf,64,64).trim();
            if (note.length() > 0)
            {
                CustLog noteLog = new CustLog("D" + currCust.AcctNum, conversionDate);
                noteLog.addLine("Note: Tank " + currCust.TankNum + " (D" + currCust.OrigAcctNum + ")");
                noteLog.addLine(note);
                currCust.addLog(noteLog);
                currCust.setNote(note);
            }

            String extNote = new String(notesBuf,272,18);
            extNote += new String(notesBuf,292,18);
            extNote += new String(notesBuf,312,18);
            extNote += new String(notesBuf,332,18);
            extNote += new String(notesBuf,352,18);
            extNote += new String(notesBuf,372,18);
            extNote += new String(notesBuf,392,18);
            extNote += new String(notesBuf,412,18);
            extNote += new String(notesBuf,432,18);
            extNote += new String(notesBuf,452,18);
            extNote += new String(notesBuf,472,18);
            extNote += new String(notesBuf,492,18);
            extNote = extNote.trim();
            if (extNote.length() > 0)
            {
                CustLog extNoteLog = new CustLog("D" + currCust.AcctNum, conversionDate);
                extNoteLog.addLine("Extended note: Tank " + currCust.TankNum + " (D" + currCust.OrigAcctNum + ")");
                extNoteLog.addLine(extNote);
                currCust.addLog(extNoteLog);
                currCust.setExtNote(extNote);
            }
                
            CustLog delHist = new CustLog("D" + currCust.AcctNum, conversionDate);
            delHist.addLine("Delivery history: Tank " + currCust.TankNum + " (D" + currCust.OrigAcctNum + ")");
            double ldg = 0.0;
            boolean partial = false;
            for (int offset = 128; offset < (128 + 120); offset+=20)
            {
                String s = new String(notesBuf, offset, 20).trim();
                ldgMatcher.reset(s);
                double dg;
                boolean part = false;
                if (ldgMatcher.matches())
                {
                    String dgs = ldgMatcher.group(1);
                    dg = Double.valueOf(dgs);
                    if (ldgMatcher.group(2) != null)
                    {
                        String p = ldgMatcher.group(2);
                        if (!p.equals(""))
                            part = true;
                    }
                }
                else
                    dg = 0;
                if (dg > 0.0)
                {
                    ldg = dg;
                    partial = part;
                }
                delHist.addLine(s + "  ////  ");
            }   
            currCust.addLog(delHist);
            currCust.setLastDeliveryGallons(ldg);
            currCust.setLastDeliveryPartial(partial);
        }
    }

    public void WriteCSVFiles(OutputStream data_os, OutputStream notes_os)
    {
        PrintStream output = new PrintStream(data_os);
        
        output.println(DunnCustomer.header());
        for (DunnCustomer cust : AllCustomers)
        {
            if (cust != null)
                output.println(cust.toString());
        }
        output.close();
        
        output = new PrintStream(notes_os);
        for (DunnCustomer cust : AllCustomers)
        {
            if (cust == null)
                continue;
            if (cust.getSite() != null &&
                cust.getSite().trim().length() > 0)
                output.println(cust.AcctNum + ",\"" + cust.getSite() + "\"");
            if (cust.getNote() != null &&
                    cust.getNote().trim().length() > 0)
                    output.println(cust.AcctNum + ",\"" + cust.getNote() + "\"");
            if (cust.getExtNote() != null &&
                cust.getExtNote().trim().length() > 0)
                output.println(cust.AcctNum + ",\"" + cust.getExtNote() + "\"");
        }
    }

    
    public void WriteMailingList(OutputStream list_os)
    {
        PrintStream output = new PrintStream(list_os);
        
        output.println(DunnCustomer.mailingHeader());
        for (Integer custNum : Customers)
        {
            DunnCustomer cust = AllCustomers[custNum];
            if (cust != null)
                output.println(cust.mailingString());
        }
        output.close();
    }
    
    
    public void WriteMultiTankList(OutputStream data_os)
    {
        PrintStream output = new PrintStream(data_os);
        
        output.println(DunnCustomer.header());
 
        for (String custName : MultiTanks.keySet())
        {
            List<Integer> mult = MultiTanks.get(custName);
            if (mult.size() < 2)
                continue;
            for (int acctNum : mult)
            {
                DunnCustomer cust = AllCustomers[acctNum];
                if (cust == null)
                    continue;
                output.println(cust.toString());
            }
        }
        output.close();
    }
    
    private Tank CustToTank(DunnCustomer cust) throws Exception
    {
        Tank t = new Tank("D" + cust.AcctNum, "D" + cust.OrigAcctNum, cust.TankNum);
        
        t.setName(cust.getDeliveryPrefix(),
                  cust.getDeliveryFirstName(),
                  cust.getDeliveryMidInitial(),
                  cust.getDeliveryLastName(),
                  cust.getDeliverySuffix());
        t.setStreet1(cust.getDeliveryAddr());
        t.setDelRefInfo("");
        if (cust.getDeliveryCity() == null)
            t.setTownStateZip(cust.getDeliveryCityStateZip());
        else
        {
            t.setTown(cust.getDeliveryCity());
            t.setState(cust.getDeliveryState());
        }
        t.setZip(cust.getDeliveryZipPlus4());
        t.setCounty(cust.getCounty());
        if (cust.getType() != CType.RESIDENTIAL)
            t.setCommercial(true);
        
        t.setDelInstr(cust.getSite());
        
        t.setTankSize(cust.getTankSize(), true);
        t.setProduct(cust.getProduct());
        if (cust.isHotWater())
            t.setHotWaterGallons(30);
        else
            t.setHotWaterGallons(0);
        t.setSeparateHotWater(false);
            
        if (cust.getDeliveryType() == DunnCustomer.DelType.AUTOMATIC)
            t.setWillCall(false);
        else
            t.setWillCall(true);
        
        if (cust.getScheduleType() == DunnCustomer.SchedType.CALENDAR)
        {
            t.setPeriod(true);
            t.setDBD(cust.getDaysToNext());
            t.setNextDelDate(cust.getNextDeliveryDate());
        }
        else
        {
            t.setK_Factor(cust.getK_Factor());
            t.setDegreeDayLast(cust.getDD_Last()+3428);
            t.setDegreeDayNext(cust.getDD_Next()+3428);
        }
        t.setLastDelDate(cust.getLastDelivery());
        t.setLastDelGal(cust.getLastDeliveryGallons());
        t.setLastDelPartial(cust.isLastDeliveryPartial());

        return t;
    }

    private Service CustToSvc(DunnCustomer cust) throws Exception
    {
        Service s = new Service("D" + cust.AcctNum, "D" + cust.OrigAcctNum, cust.TankNum);
        
        s.setName(cust.getDeliveryPrefix(),
                  cust.getDeliveryFirstName(),
                  cust.getDeliveryMidInitial(),
                  cust.getDeliveryLastName(),
                  cust.getDeliverySuffix());
        s.setStreet1(cust.getDeliveryAddr());
        s.setTown(cust.getDeliveryCity());
        s.setState(cust.getDeliveryState());
        s.setZip(cust.getDeliveryZipPlus4());
        s.setCounty(cust.getCounty());
        s.setZone(cust.getDeliveryZip());
        s.setSvcInstr(cust.getSite());
        return s;
    }
     
    
    public void WriteADD_Files(OutputStream os, OutputStream nos) throws Exception
    {
        PrintStream add_os  = new PrintStream(os);
        PrintStream note_os = new PrintStream(nos);
        
        for (int acct : Customers)
        {
            DunnCustomer cust = AllCustomers[acct];
            if (cust == null)
                continue;
            
            Account a = new Account("D" + cust.AcctNum);
            
            a.setSortCode(cust.getADDSortCode());
            if (cust.getBillingLastName() == null)
                a.setName(cust.getBillingName());
            else
                a.setName(cust.getBillingPrefix(),
                          cust.getBillingFirstName(),
                          cust.getBillingMidInitial(),
                          cust.getBillingLastName(),
                          cust.getBillingSuffix());
            a.setStreet1(cust.getBillingAddr());
            a.setStreet2("");
            if (cust.getBillingCity() == null)
                a.setTownState(cust.getBillingCityStateZip());
            else
            {
                a.setTownState(cust.getBillingCity(), cust.getBillingState());
                a.setZip(cust.getBillingZip());
                a.setCounty(cust.getCounty());
            }
            
            a.setAreaCode(cust.getAreaCode());
            a.setTelExch(cust.getTelExch());
            a.setTelNum(cust.getTelNum());
            
            a.setCustType(cust.getType());
            a.setGeneralText(cust.getSortCode());
            a.setLastPaymentDate(cust.getLastCredit());
            
            a.AddTank(CustToTank(cust));
            a.AddServiceLoc(CustToSvc(cust));
            
            int logNum = 1;
            for (CustLog l : cust.Logs)
            {
                l.WriteLog(note_os, logNum++);
            }
            
            for (DunnCustomer sub : cust.SubAccounts)
            {
                a.AddTank(CustToTank(sub));
                a.AddServiceLoc(CustToSvc(sub));
                for (CustLog l : sub.Logs)
                {
                    l.WriteLog(note_os, logNum++);
                }
            }
            
            a.WriteAccount(add_os);
        }
        Trailer.WriteTrailer(add_os);
        add_os.close();
    }

    
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        if (args.length != 7)
        {
            System.err.println("Invalid: extra or missing arguments.\n" +
                               "Usage:\nExtractData <pathToFiles> <dataOutputFile> <notesOutputFile> <multiOutputFile> <mailOutputFile> <addOutputFile>");
            System.exit(1);
        }
        
        try {
            OutputStream dos;
            if (args[1].equals("-"))
                dos = System.out;
            else
            {
                File dataOutFile = new File(args[1]);
                if (!dataOutFile.exists())
                    dataOutFile.createNewFile();
                if (!dataOutFile.isFile())
                    throw new Exception("Data output file is not a file");
                if (!dataOutFile.canWrite())
                    throw new Exception("Data output file is not writable");
                dos = new FileOutputStream(dataOutFile, false);
            }

            OutputStream nos;
            if (args[2].equals("-"))
                nos = System.out;
            else
            {
                File noteOutFile = new File(args[2]);
                if (!noteOutFile.exists())
                    noteOutFile.createNewFile();
                if (!noteOutFile.isFile())
                    throw new Exception("Note output file is not a file");
                if (!noteOutFile.canWrite())
                    throw new Exception("Note output file is not writable");
                nos = new FileOutputStream(noteOutFile, false);
            }

            OutputStream dupOs;
            if (args[3].equals("-"))
                dupOs = System.out;
            else
            {
                File dupOutFile = new File(args[3]);
                if (!dupOutFile.exists())
                    dupOutFile.createNewFile();
                if (!dupOutFile.isFile())
                    throw new Exception("Dups output file is not a file");
                if (!dupOutFile.canWrite())
                    throw new Exception("Dups output file is not writable");
                dupOs = new FileOutputStream(dupOutFile, false);
            }

            OutputStream mailOs;
            if (args[4].equals("-"))
                mailOs = System.out;
            else
            {
                File mailOutFile = new File(args[4]);
                if (!mailOutFile.exists())
                    mailOutFile.createNewFile();
                if (!mailOutFile.isFile())
                    throw new Exception("Mailing list output file is not a file");
                if (!mailOutFile.canWrite())
                    throw new Exception("Mailing list output file is not writable");
                mailOs = new FileOutputStream(mailOutFile, false);
            }

            OutputStream addOs;
            if (args[5].equals("-"))
                addOs = System.out;
            else
            {
                File addOutFile = new File(args[5]);
                if (!addOutFile.exists())
                    addOutFile.createNewFile();
                if (!addOutFile.isFile())
                    throw new Exception("Mailing list output file is not a file");
                if (!addOutFile.canWrite())
                    throw new Exception("Mailing list output file is not writable");
                addOs = new FileOutputStream(addOutFile, false);
            }

            OutputStream addNos;
            if (args[6].equals("-"))
                addNos = System.out;
            else
            {
                File addOutFile = new File(args[6]);
                if (!addOutFile.exists())
                    addOutFile.createNewFile();
                if (!addOutFile.isFile())
                    throw new Exception("ADD Notes output file is not a file");
                if (!addOutFile.canWrite())
                    throw new Exception("ADD Notes output file is not writable");
                addNos = new FileOutputStream(addOutFile, false);
            }

            ExtractData ed = new ExtractData(args[0]);
            ed.WriteCSVFiles(dos, nos);
            ed.WriteMultiTankList(dupOs);
            ed.WriteMailingList(mailOs);
            ed.WriteADD_Files(addOs, addNos);
        }
        catch (FileNotFoundException e)
        {
            System.err.println("Error opening output file: " + e);
            System.exit(1);
        }
        catch (Exception e)
        {
            System.err.println(e);
            System.exit(1);
        }
    }
}
