/*
 * Created on Jul 31, 2007 by pladd
 *
 */
package com.bottinifuel.DunnFuel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bottinifuel.ADD_Cust_Import.CustLog;
import com.bottinifuel.ADD_Cust_Import.Account.CType;
import com.bottinifuel.ADD_Cust_Import.Tank.FuelType;

/**
 * @author pladd
 *
 */
public class DunnCustomer
{
    public final int AcctNum;
    public final int TankNum;
    public final int OrigAcctNum;

    public List<DunnCustomer> SubAccounts = new Vector<DunnCustomer>();
    
    public Vector<CustLog> Logs = new Vector<CustLog>();
    
    private DateFormat df = new SimpleDateFormat("MM/dd/yy");

    public DunnCustomer(int acctNum, int tankNum, int origAcctNum)
    {
        AcctNum = acctNum;
        TankNum = tankNum;
        OrigAcctNum = origAcctNum;
        DefaultDate.set(1960,0,1);
        LastCharge   = DefaultDate;
        LastCredit   = DefaultDate;
        LastDelivery = DefaultDate;
        NextDeliveryDate = DefaultDate; 
    }
    
    public enum DelType {
        AUTOMATIC,
        WILL_CALL,
        SEASONAL, //??? not used
        INACTIVE
    }
    public enum SchedType {
        K_FACTOR,
        DEGREE_DAY,
        CALENDAR
    }

    private String BillingName;
    private String ADDSortCode;
    private String BillingPrefix;
    private String BillingFirstName;
    private String BillingMidInitial;
    private String BillingLastName;
    private String BillingSuffix;
    private String BillingAddr;
    private String BillingCityStateZip;
    private String BillingCity;
    private String BillingState;
    private int    BillingZip;
    
    private int    AreaCode;
    private int    TelExch;
    private int    TelNum;
    
    private int    County;
    
    private String DeliveryName;
    private String DeliveryPrefix;
    private String DeliveryFirstName;
    private String DeliveryMidInitial;
    private String DeliveryLastName;
    private String DeliverySuffix;
    private String DeliveryAddr;
    private String DeliveryCityStateZip;
    private String DeliveryCity;
    private String DeliveryState;
    private int    DeliveryZipPlus4;
    private int    DeliveryZip;

    private CType    Type;
    private String   SortCode;
    private boolean  Exempt = false;
    
    private DelType   DeliveryType;
    private SchedType ScheduleType;
    private boolean   HotWater;
    private FuelType  Product;
    private int       TankSize;
    
    private double    K_Factor;
    private int       DD_Last;
    private int       DD_Next;

    private Calendar  NextDeliveryDate;
    private int       DaysToNext;

    private Calendar  LastCharge;
    private Calendar  LastCredit;

    private Calendar  LastDelivery;
    private double    LastDeliveryGallons;
    private boolean   LastDeliveryPartial;
    
    private String Site;
    private String Note;
    private String ExtNote;

    private static final String SuffixPat  = "(?:\\s(J[rR]|S[rR]|MD|M\\.D|III|IV)\\.?)?";
    private static final String PrefixPat  = "((?:M[sS]" +
                                             "|M[rR][sS]?" +
                                             "|D[rR])\\.?\\s*)?";
    private static final String MidInitPat = "(?:\\s+([A-Za-z])\\.)?";

    private static final Pattern BillNamePat = Pattern.compile("(.*?)" +   // Last Name
                                                               SuffixPat +
                                                               "\\s*,\\s*" + // Whitespace & Comma
                                                               PrefixPat +
                                                               "(.*?)" + // First name
                                                               MidInitPat);
    private static final Matcher BillNameMatcher = BillNamePat.matcher("");

    private static final Pattern DelNamePat = Pattern.compile(PrefixPat + 
                                                              "((?:\\S+)|(?:\\S+\\s*\\&\\s*\\S+))" + // First Name
                                                              MidInitPat + 
                                                              "\\s*((?:\\S*?)|(?:V[aA][nN]|M[aA]?[cC]|L[eE]|L[aA]|D[eE])\\s+\\S*)\\s*" + // Last Name
                                                              SuffixPat);
    private static final Matcher DelNameMatcher = DelNamePat.matcher("");
    
    private static final Calendar DefaultDate = Calendar.getInstance();
    private static final Pattern CszPat = Pattern.compile("([^,]+)" +               // City
                                                          "\\s*,?\\s*" +            // Comma & Whitespace
                                                          "(\\p{Upper}{2})\\s*" +   // State & Whitespace
                                                          "(\\p{Digit}{5})" +       // 5 digit zip 
                                                          "(?:-(\\p{Digit}{4}))?" + // optional zip+4
                                                          "\\s*"                    // trailing whitespace
                                                          );
    private static final Matcher CszMatcher = CszPat.matcher("");

    private static final Pattern MultPat = Pattern.compile("(.*)\\(\\d+\\)\\s*");
    private static final Matcher MultMatcher = MultPat.matcher("");

    
    public String getBillingName()
    {
        if (BillingLastName != null)
            return BillingPrefix + " " + BillingFirstName + " " + BillingMidInitial +
            " " + BillingLastName + " " + BillingSuffix;
        else
            return BillingName;
    }
    public void setBillingName(String billingName)
    {
        BillNameMatcher.reset(billingName.trim());
        if (BillNameMatcher.matches())
        {
            if (BillNameMatcher.group(1) == null)
            {
                BillingLastName = "";
                ADDSortCode = "";
            }
            else
            {
                BillingLastName = BillNameMatcher.group(1).trim();
                ADDSortCode = BillingLastName.substring(0, (BillingLastName.length()<6)?BillingLastName.length():6).toUpperCase();
                int spaceIndex = ADDSortCode.indexOf(' ');
                if (spaceIndex >= 0 && spaceIndex < 6)
                    ADDSortCode = ADDSortCode.substring(0, spaceIndex);
                int foo = 0;
            }

            if (BillNameMatcher.group(2) == null)
                BillingSuffix = "";
            else
            {
                BillingSuffix = BillNameMatcher.group(2);
                if (BillingSuffix.equals("M.D"))
                    BillingSuffix = "MD";
            }

            if (BillNameMatcher.group(3) == null)
                BillingPrefix = "";
            else
                BillingPrefix = BillNameMatcher.group(3).trim();

            if (BillNameMatcher.group(4) == null)
                BillingFirstName = "";
            else
                BillingFirstName  = BillNameMatcher.group(4).trim();

            if (BillNameMatcher.group(5) == null)
                BillingMidInitial = "";
            else
                BillingMidInitial = BillNameMatcher.group(5).trim();
        }
        else
            ADDSortCode = "";

        BillingName = billingName.trim();        
    }
    
    public String getBillingAddr()
    {
        return BillingAddr;
    }
    public void setBillingAddr(String billingAddr)
    {
        BillingAddr = billingAddr.replaceFirst("P(?:\\.|\\s+)O(?:\\.\\s*|\\s+)B[oO0][xX]\\s*", "PO BOX ").trim();
    }
    public String getBillingCityStateZip()
    {
        return BillingCityStateZip;
    }
    public boolean setBillingCityStateZip(String billingCityStateZip)
    {
        BillingCityStateZip = billingCityStateZip.trim();
        CszMatcher.reset(BillingCityStateZip);
        if (CszMatcher.matches())
        {
            BillingCity  = CszMatcher.group(1).trim();
            BillingState = CszMatcher.group(2).trim();
            int zip      = Integer.valueOf(CszMatcher.group(3));
            int plus4 = 0;
            if (CszMatcher.groupCount() > 3 && CszMatcher.group(4) != null)
                plus4 = Integer.valueOf(CszMatcher.group(4));
            BillingZip = (zip * 10000) + plus4;
        }
        else
        {
            System.err.print("Acct #" + OrigAcctNum + " can't parse billing addr: " + billingCityStateZip + "\n");
            return false;
        }
        return true;
    }
    public String getDeliveryName()
    {
        if (DeliveryName == null)
            return DeliveryPrefix + " " + DeliveryFirstName + " " + DeliveryMidInitial +
            " " + DeliveryLastName + " " + DeliverySuffix;
        else
            return DeliveryName;
    }
    public void setDeliveryName(String deliveryName)
    {
        MultMatcher.reset(deliveryName.trim());
        if (MultMatcher.matches())
            DeliveryName = MultMatcher.group(1).trim();
        else
            DeliveryName = deliveryName.trim();
        
        DelNameMatcher.reset(DeliveryName);
        if (DelNameMatcher.matches())
        {
            if (DelNameMatcher.group(1) == null)
                DeliveryPrefix = "";
            else
                DeliveryPrefix = DelNameMatcher.group(1).trim();

            if (DelNameMatcher.group(2) == null)
                DeliveryFirstName = "";
            else
                DeliveryFirstName  = DelNameMatcher.group(2).trim();

            if (DelNameMatcher.group(3) == null)
                DeliveryMidInitial = "";
            else
                DeliveryMidInitial = DelNameMatcher.group(3).trim();

            if (DelNameMatcher.group(4) == null)
                DeliveryLastName = "";
            else
                DeliveryLastName = DelNameMatcher.group(4).trim();

            if (DelNameMatcher.group(5) == null)
                DeliverySuffix = "";
            else
            {
                DeliverySuffix = DelNameMatcher.group(5);
                if (DeliverySuffix.equals("M.D"))
                    DeliverySuffix = "MD";
            }
        }
        else
        {
            DeliveryLastName   = DeliveryName;
            DeliveryFirstName  = "";
            DeliveryPrefix     = "";
            DeliverySuffix     = "";
            DeliveryMidInitial = "";
        }
    }
    public String getDeliveryAddr()
    {
        return DeliveryAddr;
    }
    public void setDeliveryAddr(String deliveryAddr)
    {
        DeliveryAddr = deliveryAddr.trim();
    }
    public String getDeliveryCityStateZip()
    {
        return DeliveryCityStateZip;
    }
    public boolean setDeliveryCityStateZip(String deliveryCityStateZip)
    {
        DeliveryCityStateZip = deliveryCityStateZip.trim();
        CszMatcher.reset(deliveryCityStateZip.trim());
        if (CszMatcher.matches())
        {
            DeliveryCity  = CszMatcher.group(1).trim();
            DeliveryState = CszMatcher.group(2).trim();
            DeliveryZip   = Integer.valueOf(CszMatcher.group(3));
            int plus4 = 0;
            if (CszMatcher.groupCount() > 3 && CszMatcher.group(4) != null)
                plus4 = Integer.valueOf(CszMatcher.group(4));
            DeliveryZipPlus4 = (DeliveryZip * 10000) + plus4;
            
            if (DeliveryZip == 12740 ||
                DeliveryZip == 12789)
                County = 5;
            else
                County = 8;
        }
        else
        {
            System.err.print("Acct #" + OrigAcctNum + " can't parse delivery addr: " + deliveryCityStateZip + "\n");
            County = 3;
            return false;
        }
        return true;
    }
    public String getSortCode()
    {
        return SortCode;
    }
    public void setSortCode(String sortCode)
    {
        SortCode = sortCode.trim();
    }
    public String getSite()
    {
        return Site;
    }
    public void setSite(String site)
    {
        Site = site.trim();
    }
    public String getNote()
    {
        return Note;
    }
    public void setNote(String note)
    {
        Note = note.trim();
    }
    public String getExtNote()
    {
        return ExtNote;
    }
    public void setExtNote(String extendedNote)
    {
        ExtNote = extendedNote.trim();
    }
    
    public static String header()
    {
        return "AcctNum,TankNum,OrigAcctNum,SortCode,LastCharge,LastCredit,LastDelivery,DelType,SchedType,HWH,TankSize,Product,K,DD_Last,DD_Next," +
            "Type,TypeCode,Exempt," +
            "BillName,BillPrefix,BillFirstName,BillMidInit,BillLastName,BillSuffix," +
            "BillAddr,BillCSZ,BillCity,BillState,BillZip,Tel#," +
            "DelName,DelAddr,DelCSZ,DelCity,DelState,DelZipPlus4,DelZip,DIN";
    }

    public static String mailingHeader()
    {
        return "AcctNum,LastCharge,LastCredit,LastDelivery,"+
        "BillName,BillPrefix,BillFirstName,BillMidInit,BillLastName,BillSuffix,"+
        "BillAddr,BillCSZ,BillCity,BillState,BillZip,Tel#";
    }

    public String mailingString()
    {
        String rc =
            AcctNum          + "," +
            df.format(LastCharge.getTime())   + "," + 
            df.format(LastCredit.getTime())   + "," + 
            df.format(LastDelivery.getTime()) + ",\"";
        
        if (BillingLastName == null)
            rc +=
                BillingName      + "\",,,,,,,\"";
        else
            rc +=
                BillingName      + "\",\"" +
                BillingPrefix    + "\",\"" +
                BillingFirstName + "\",\"" +
                BillingMidInitial+ "\",\"" +
                BillingLastName  + "\",\"" +
                BillingSuffix    + "\",\"";

        rc +=
            BillingAddr      + "\",\"";

        if (BillingCity != null)
            rc +=
                "\",\"" +
                BillingCity + "\",\"" +
                BillingState + "\"," +
                BillingZip + ",\"";
        else
            rc += 
                BillingCityStateZip + "\",,,,\"";
        rc +=
            getTelNumStr() + "\"";
        return rc;
    }
    
    
    public String toString()
    {
        String rc =
            AcctNum         + "," +
            TankNum         + "," +
            OrigAcctNum     + ",\"" +
            SortCode        + "\"," +
            df.format(LastCharge.getTime())   + "," + 
            df.format(LastCredit.getTime())   + "," + 
            df.format(LastDelivery.getTime()) + "," + 
            DeliveryType    + "," +
            ScheduleType    + "," +
            HotWater        + "," +
            TankSize        + "," +
            Product.CodeNum + ",";

        if (ScheduleType == SchedType.K_FACTOR)
            rc +=
            K_Factor        + "," +
            DD_Last         + "," +
            DD_Next         + ",";
        else
            rc +=
            DaysToNext      + "," +
            df.format(LastDelivery.getTime()) + "," +
            df.format(NextDeliveryDate.getTime()) + ",";
        
        rc +=
            Type             + "," +
            Type.CodeNum     + "," +
            Exempt           + ",\"" +
            BillingName      + "\",\"";

        if (BillingLastName == null)
            rc += "\",,,,,,\"";
        else
            rc +=
                BillingPrefix    + "\",\"" +
                BillingFirstName + "\",\"" +
                BillingMidInitial+ "\",\"" +
                BillingLastName  + "\",\"" +
                BillingSuffix    + "\",\"";
        
        rc +=
            BillingAddr      + "\",\"";
        
        if (BillingCity != null)
            rc +=
                "\",\"" +
                BillingCity + "\",\"" +
                BillingState + "\"," +
                BillingZip + ",\"";
        else
            rc += 
                BillingCityStateZip + "\",,,,\"";
            
        rc += 
            getTelNumStr()  + "\",\"" +
            DeliveryName    + "\",\"" +
            DeliveryAddr    + "\",\"";

        if (DeliveryCity != null)
            rc +=
                "\",\"" +
                DeliveryCity     + "\",\"" +
                DeliveryState    + "\"," +
                DeliveryZipPlus4 + "," +
                DeliveryZip      + ",\"";
        else
            rc += 
                DeliveryCityStateZip + "\",,,,,\"";

        rc +=
            Site            + "\"";

        return rc;
    }
    public DelType getDeliveryType()
    {
        return DeliveryType;
    }
    public void setDeliveryType(DelType deliveryType)
    {
        DeliveryType = deliveryType;
    }
    public SchedType getScheduleType()
    {
        return ScheduleType;
    }
    public void setScheduleType(SchedType scheduleType)
    {
        ScheduleType = scheduleType;
    }
    public boolean isHotWater()
    {
        return HotWater;
    }
    public void setHotWater(boolean hotWater)
    {
        HotWater = hotWater;
    }
    public double getK_Factor() throws Exception
    {
        if (ScheduleType != SchedType.K_FACTOR)
            throw new Exception("Can't retrieve K Factor on non K_FACTOR account");
        return K_Factor;
    }
    public void setK_Factor(double factor) throws Exception
    {
        if (ScheduleType != SchedType.K_FACTOR)
            throw new Exception("K Factor set on non K_FACTOR account");
        K_Factor = factor;
    }
    public int getTankSize()
    {
        return TankSize;
    }
    public void setTankSize(int tankSize)
    {
        TankSize = tankSize;
    }
    public int getDD_Last() throws Exception
    {
        if (ScheduleType != SchedType.K_FACTOR)
            throw new Exception("Can't retrieve DD_Last on non K_FACTOR account");
        return DD_Last;
    }
    public void setDD_Last(int last) throws Exception
    {
        if (ScheduleType != SchedType.K_FACTOR)
            throw new Exception("DD_Last set on non K_FACTOR account");
        DD_Last = last;
    }
    public int getDD_Next() throws Exception
    {
        if (ScheduleType != SchedType.K_FACTOR)
            throw new Exception("Can't retrieve DD_Next on non K_FACTOR account");
        return DD_Next;
    }
    public void setDD_Next(int next) throws Exception
    {
        if (ScheduleType != SchedType.K_FACTOR)
            throw new Exception("DD_Next set on non K_FACTOR account");
        DD_Next = next;
    }
    public FuelType getProduct()
    {
        return Product;
    }
    public void setProduct(FuelType product)
    {
        Product = product;
    }
    public CType getType()
    {
        return Type;
    }
    public void setType(CType type)
    {
        Type = type;
    }
    public boolean isExempt()
    {
        return Exempt;
    }
    public void setExempt(boolean exempt)
    {
        Exempt = exempt;
    }
    public Calendar getLastCharge()
    {
        return LastCharge;
    }
    public void setLastCharge(Calendar lastCharge)
    {
        LastCharge = lastCharge;
    }
    
    public Calendar BufToCal(byte [] dataBuf, int index)
    {
        int date,day,month,year;
        Calendar cal;
        cal = Calendar.getInstance();
        date  = (0xFFFF) & ByteBuffer.wrap(dataBuf).order(ByteOrder.BIG_ENDIAN).getShort(index);
        day   = date % 32;
        month = (date >> 5) % 16;
        year  = (date >> 9) + 1900;
        if (day != 0 && month != 0 && year != 0)
            cal.set(year,month-1,day);
        else
            cal.set(1960,0,1);
        return cal;
    }
    public void setLastCharge(byte [] dataBuf, int index)
    {
        LastCharge = BufToCal(dataBuf, index);
    }
    public Calendar getLastCredit()
    {
        return LastCredit;
    }
    public void setLastCredit(byte [] dataBuf, int index)
    {
        LastCredit = BufToCal(dataBuf, index);
    }
    public void setLastCredit(Calendar lastCredit)
    {
        LastCredit = lastCredit;
    }
    public Calendar getLastDelivery()
    {
        return LastDelivery;
    }
    public void setLastDelivery(byte [] dataBuf, int index)
    {
        LastDelivery = BufToCal(dataBuf, index);
    }
    public void setLastDelivery(Calendar lastDelivery)
    {
        LastDelivery = lastDelivery;
    }
    public Calendar getNextDeliveryDate() throws Exception
    {
        if (ScheduleType != SchedType.CALENDAR)
            throw new Exception("Can't retrieve NextDeliveryDate on non CALENDAR account");
        return NextDeliveryDate;
    }
    public void setNextDeliveryDate(byte [] dataBuf, int index) throws Exception
    {
        if (ScheduleType != SchedType.CALENDAR)
            throw new Exception("NextDeliveryDate set on non CALENDAR account");
        NextDeliveryDate = BufToCal(dataBuf, index);
    }
    public int getDaysToNext()
    {
        return DaysToNext;
    }
    public void setDaysToNext(int daysToNext)
    {
        DaysToNext = daysToNext;
    }
    public String getBillingCity()
    {
        return BillingCity;
    }
    public void setBillingCity(String billingCity)
    {
        BillingCity = billingCity.trim();
    }
    public String getBillingState()
    {
        return BillingState;
    }
    public void setBillingState(String billingState)
    {
        BillingState = billingState.trim();
    }
    public int getBillingZip()
    {
        return BillingZip;
    }
    public void setBillingZip(int billingZip)
    {
        BillingZip = billingZip;
    }
    public int getCounty()
    {
        return County;
    }
    public void setCounty(int county)
    {
        County = county;
    }
    public String getDeliveryCity()
    {
        return DeliveryCity;
    }
    public void setDeliveryCity(String deliveryCity)
    {
        DeliveryCity = deliveryCity.trim();
    }
    public String getDeliveryState()
    {
        return DeliveryState;
    }
    public void setDeliveryState(String deliveryState)
    {
        DeliveryState = deliveryState.trim();
    }
    public int getDeliveryZipPlus4()
    {
        return DeliveryZipPlus4;
    }
    public void setDeliveryZipPlus4(int deliveryZipPlus4)
    {
        DeliveryZipPlus4 = deliveryZipPlus4;
    }
    public int getDeliveryZip()
    {
        return DeliveryZip;
    }
    public void setDeliveryZip(int deliveryZip)
    {
        DeliveryZip = deliveryZip;
    }
    public String getADDSortCode()
    {
        return ADDSortCode;
    }
    public String getBillingPrefix()
    {
        return BillingPrefix;
    }
    public String getBillingFirstName()
    {
        return BillingFirstName;
    }
    public String getBillingMidInitial()
    {
        return BillingMidInitial;
    }
    public String getBillingLastName()
    {
        return BillingLastName;
    }
    public String getBillingSuffix()
    {
        return BillingSuffix;
    }
    public int getAreaCode()
    {
        return AreaCode;
    }
    public void setAreaCode(int areaCode)
    {
        AreaCode = areaCode;
    }
    public int getTelExch()
    {
        return TelExch;
    }
    public void setTelExch(int telExch)
    {
        TelExch = telExch;
    }
    public void setTelNum(int telNum)
    {
        TelNum = telNum;
    }
    public String getTelNumStr()
    {
        DecimalFormat threeDigit = new DecimalFormat("000");
        DecimalFormat fourDigit  = new DecimalFormat("0000");

        return "(" + AreaCode + ")" + threeDigit.format(TelExch) + "-" + fourDigit.format(TelNum);
    }
    public int getTelNum()
    {
        return TelNum;
    }
    
    public void addLog(CustLog l)
    {
        Logs.add(l);
    }
    public String getDeliveryPrefix()
    {
        return DeliveryPrefix;
    }
    public void setDeliveryPrefix(String deliveryPrefix)
    {
        DeliveryPrefix = deliveryPrefix;
    }
    public String getDeliveryFirstName()
    {
        return DeliveryFirstName;
    }
    public void setDeliveryFirstName(String deliveryFirstName)
    {
        DeliveryFirstName = deliveryFirstName;
    }
    public String getDeliveryMidInitial()
    {
        return DeliveryMidInitial;
    }
    public void setDeliveryMidInitial(String deliveryMidInitial)
    {
        DeliveryMidInitial = deliveryMidInitial;
    }
    public String getDeliveryLastName()
    {
        return DeliveryLastName;
    }
    public void setDeliveryLastName(String deliveryLastName)
    {
        DeliveryLastName = deliveryLastName;
    }
    public String getDeliverySuffix()
    {
        return DeliverySuffix;
    }
    public void setDeliverySuffix(String deliverySuffix)
    {
        DeliverySuffix = deliverySuffix;
    }
    public double getLastDeliveryGallons()
    {
        return LastDeliveryGallons;
    }
    public void setLastDeliveryGallons(double lastDeliveryGallons)
    {
        LastDeliveryGallons = lastDeliveryGallons;
    }
    public boolean isLastDeliveryPartial()
    {
        return LastDeliveryPartial;
    }
    public void setLastDeliveryPartial(boolean lastDeliveryPartial)
    {
        LastDeliveryPartial = lastDeliveryPartial;
    }
}
