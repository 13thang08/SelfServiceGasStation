/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package utility;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacardx.framework.math.BigNumber;
import javacardx.framework.tlv.BERTLV;
import javacardx.framework.tlv.BERTag;
import javacardx.framework.tlv.ConstructedBERTLV;
import javacardx.framework.tlv.ConstructedBERTag;
import javacardx.framework.tlv.PrimitiveBERTLV;
import javacardx.framework.tlv.PrimitiveBERTag;
import javacardx.framework.tlv.TLVException;
import javacardx.framework.util.intx.JCint;

/**
 *
 * @author Ariya
 */
public class SelfServiceGasStation extends Applet {
    /**
     * CLA value for SelfServiceGasStation applet
     */
    final static byte SSGS_CLA = (byte) 0x80;
    
    /**
     * Temp PIN (need edit)
     */
    final static byte[] TEMP_PIN = {(byte) 0x01, (byte) 0x02, (byte) 0x03};
    
    /**
     * INS value for VERIFY command
     */
    final static byte VERIFY = (byte) 0x01;
    
    /**
     * INS value for update purchase info
     */
    final static byte UPDATE_PURCHASE_INFO = (byte) 0x02;
    
    /**
     * INS value for get balance command
     */
    final static byte GET_BALANCE = (byte) 0x03;
    
    /**
     * INS value for get purchase histories
     */
    final static byte GET_PURCHASE_HISTORIES = (byte) 0x04;
    
    /**
     * Initial account balance
     */
    final static byte[] INITIAL_ACCOUNT_BALANCE = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x00};
    
    /**
     * dummy signature
     */
    private static final byte[] dummySignature = {(byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x88};
    
    /**
     * Maximum number of incorrect tries before the PIN is blocked
     */
    final static byte MAX_PIN_TRIES = (byte) 0x03;
    
    /**
     * Maximum PIN size
     */
    final static byte MAX_PIN_SIZE = (byte) 0x08;
    
    /**
     * SW bytes for PIN verification failed
     */
    final static short SW_VERIFICATION_FAILED = 0x6300;
    
    /**
     * SW bytes when access without PIN verification
     */
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6301;
    
    /**
     * SW bytes when account balance not enough to transaction
     */
    final static short SW_NOT_ENOUGH_ACCOUNT_BALANCE = 0x6302;
    
    /**
     * SW bytes for invalid update purchase information
     */
    final static short INVALID_UPDATE_PURCHASE_INFO = 0x6303;
    
    /**
     * SW bytes for invalid station signature
     */
    final static short INVALID_STATION_SIGNATURE = 0x6304;
    
    /**
     * SW bytes for TLV exception
     */
    final static short TLV_EXCEPTION = 0x6305;
    
    /**
     * SW bytes for arithmetic exception
     */
    final static short ARITHMETIC_EXCEPTION = 0x6306;
    
    /**
     * SW bytes when read invalid format number
     */
    final static short INVAILD_NUMBER_FORMAT = 0x6307;
    
    /**
     * SW bytes when purchase info is empty (or purchase info not exist - future function)
     */
    final static short SW_PURCHASE_INFO_NOT_FOUND = 0x6308;
    
    /**
     * The user PIN
     */
    private OwnerPIN pin;
    
    /**
     * Amount of money in user's account
     */
    private BigNumber accountBalance;
    
    /**
     * This constructed BER TLV holds the purchase histories
     */
    private ConstructedBERTLV purchaseHistories;
    
    /**
     * Constructed BER TLV Tag for purchase histories
     */
    ConstructedBERTag purchaseHistoriesTag;
    
    /**
     * Constructed BER TLV Tag for purchase Info
     */
    ConstructedBERTag purchaseInfoTag;
    
    /**
     * Primitive BER TLV Tag for station ID
     */
    PrimitiveBERTag stationIDTag;
    
    /**
     * Primitive BER TLV Tag for buy time
     */
    PrimitiveBERTag buyTimeTag;
    
    /**
     * Primitive BER TLV Tag for amount of gasoline
     */
    PrimitiveBERTag amountTag;
    
    /**
     * Primitive BER TLV Tag for price of gasoline
     */
    PrimitiveBERTag priceTag;
    
    /**
     * Primitive BER TLV Tag for station signature
     */
    PrimitiveBERTag signatureTag;
    
    /**
     * Constructed BER TLV Tag for update purchase info message
     */
    ConstructedBERTag purchaseUpdateMes;
    
    /**
     * Big number for temporary calculations
     */
    BigNumber tempBigNum;
    
    /**
     * Temporary buffer used as scratch space
     */
    byte[] scratchSpace;

    /**
     * Installs this applet.
     * 
     * @param bArray
     *            the array containing installation parameters
     * @param bOffset
     *            the starting offset in bArray
     * @param bLength
     *            the length in bytes of the parameter data in bArray
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SelfServiceGasStation(bArray, bOffset, bLength);
    }
    
    /**
     * Select method
     */
    public boolean select() {
        // The applet declines to be selected if the pin is blocked
        if (pin.getTriesRemaining() == 0) {
            return false;
        }
        return true;
    }
    
    /**
     * Deselect method
     */
    public void deselect() {
        // reset the pin value
        pin.reset();
    }

    /**
     * Only this class's install method should create the applet object.
     */
    protected SelfServiceGasStation(byte[] bArray, short bOffset, byte bLength) {
        
        // get the pin in parameter
        byte iLen = bArray[bOffset]; // aid length
        bOffset = (short) (bOffset + iLen + 1);
        byte cLen = bArray[bOffset]; // control info
        bOffset = (short) (bOffset + cLen + 1);
        byte aLen = bArray[bOffset]; // data length: PIN length
        
        // Initialize PIN
        pin = new OwnerPIN(MAX_PIN_TRIES, MAX_PIN_SIZE);
        pin.update(bArray, (short) (bOffset + 1), aLen); // bOffset + 1: offset of the PIN
        
        // Initialize account balance to 100,000
        accountBalance = new BigNumber((byte) 8);
        accountBalance.init(INITIAL_ACCOUNT_BALANCE, (byte) 0, (byte) INITIAL_ACCOUNT_BALANCE.length, BigNumber.FORMAT_BCD);
        
        // Initialize the temporary big number
        tempBigNum = new BigNumber(BigNumber.getMaxBytesSupported());
        
        // Initialize the scatchSpace
        scratchSpace = JCSystem.makeTransientByteArray((short) 10, JCSystem.CLEAR_ON_DESELECT);
        
        // Initialize primitive tags
        initPrimitiveTags();
        
        // Initialize constructed tags
        initConstructedTags();
        
        // create an empty purchase histories
        purchaseHistoriesTag.toBytes(scratchSpace, (short) 0);
        purchaseHistories = (ConstructedBERTLV) BERTLV.getInstance(scratchSpace, (short) 0, (short) 2);
        
        // register the apple to JCRE
        register();
    }
    
    /**
     * Initialize the constructed tags
     */
    private void initConstructedTags() {
        purchaseHistoriesTag = new ConstructedBERTag();
        purchaseInfoTag = new ConstructedBERTag();
        purchaseUpdateMes = new ConstructedBERTag();
        
        purchaseHistoriesTag.init((byte) 3, (short) 1);
        purchaseInfoTag.init((byte) 3, (short) 2);
        purchaseUpdateMes.init((byte) 3, (short) 3);
    }
    
    /**
     * Initialize the primitive tags
     */
    private void initPrimitiveTags() {
        stationIDTag = new PrimitiveBERTag();
        buyTimeTag = new PrimitiveBERTag();
        amountTag = new PrimitiveBERTag();
        priceTag = new PrimitiveBERTag();
        signatureTag = new PrimitiveBERTag();
        
        stationIDTag.init((byte) 3, (short) 4);
        buyTimeTag.init((byte) 3, (short) 5);
        amountTag.init((byte) 3, (short) 6);
        priceTag.init((byte) 3, (short) 7);
        signatureTag.init((byte) 3, (short) 8);
    }

    /**
     * Processes an incoming APDU.
     * 
     * @see APDU
     * @param apdu
     *            the incoming APDU
     */
    public void process(APDU apdu) {
        // get the APDU buffer
        byte buffer[] = apdu.getBuffer();
        
        // return if this APDU is for applet selection
        if (selectingApplet()) {
            return;
        }
        
        // check if CLA is not correct
        if (buffer[ISO7816.OFFSET_CLA] != SSGS_CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        
        // get the data part of the APDU if this is update purchase info command or is verify command
        if (buffer[ISO7816.OFFSET_INS] != GET_BALANCE && buffer[ISO7816.OFFSET_INS] != GET_PURCHASE_HISTORIES) {
            apdu.setIncomingAndReceive();
        }
        
        // process the verify command
        if (buffer[ISO7816.OFFSET_INS] == VERIFY) {
            verify(buffer);
            return;
        }
        
        // if the pin is not validate, declines the access
        if (!pin.isValidated()) {
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
        }
        
        short responseSize = 0;
        
        switch (buffer[ISO7816.OFFSET_INS]) {
            case UPDATE_PURCHASE_INFO:
                updatePurchaseInfo(buffer);
                break;
            case GET_BALANCE:
                responseSize = getBalance(buffer);
                break;
            case GET_PURCHASE_HISTORIES:
                responseSize = getPurchaseHistories(buffer);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        
        // send the response data back
        apdu.setOutgoingAndSend((short) 0, (short) responseSize);
        
    }
    
    /**
     * verifies the PIN
     */
    private void verify(byte[] buffer) {
        byte numBytes = buffer[ISO7816.OFFSET_LC]; // numbytes of pin
        
        // verify PIN
        if (pin.check(buffer, ISO7816.OFFSET_CDATA, numBytes) == false) {
            ISOException.throwIt(SW_VERIFICATION_FAILED);
        }
    }
    
    /**
     * update the purchase info
     */
    private void updatePurchaseInfo(byte[] buffer) {
        short offset = ISO7816.OFFSET_CDATA;
        
        // check the Tag is constructed and private
        if (!BERTag.isConstructed(buffer, offset) || BERTag.tagClass(buffer, offset) != (byte) 3) {
            ISOException.throwIt(INVALID_UPDATE_PURCHASE_INFO);
        }
        
        // check the Tag is purchaseUpdateMes
        short purchaseUpdateMesNumber = BERTag.tagNumber(buffer, offset);
        if (purchaseUpdateMesNumber != purchaseUpdateMes.tagNumber()) {
            ISOException.throwIt(INVALID_UPDATE_PURCHASE_INFO);
        }
        
        // verify the station signature
        verifyStationSignature(buffer, offset);
        
        // if the signature is validated, update the account balance
        
        // get the amount TLV and value offset
        amountTag.toBytes(scratchSpace, (short) 0);
        short amountTLVOffset = ConstructedBERTLV.find(buffer, offset, scratchSpace, (short) 0);
        
        // get the price TLV and value offset
        priceTag.toBytes(scratchSpace, (short) 0);
        short priceTLVOffset = ConstructedBERTLV.find(buffer, offset, scratchSpace, (short) 0);
        
        // get the stationID TLV offset
        stationIDTag.toBytes(scratchSpace, (short) 0);
        short stationIDTLVOffset = ConstructedBERTLV.find(buffer, offset, scratchSpace, (short) 0);
        
        // get the buyTime TLV offset
        buyTimeTag.toBytes(scratchSpace, (short) 0);
        short buyTimeTLVOffset = ConstructedBERTLV.find(buffer, offset, scratchSpace, (short) 0);
        
        ConstructedBERTLV newPurchaseInfo = createNewPurchaseInfoTLV(buffer, stationIDTLVOffset, buyTimeTLVOffset, amountTLVOffset, priceTLVOffset);
        
        // if no error, update purchase histories and account balance
        purchaseHistories.append(newPurchaseInfo);
        updateAccountBalance(buffer, amountTLVOffset, priceTLVOffset);
        
        
    }
    
    /**
     * get balance function
     */
    private short getBalance(byte[] buffer) {
        if (buffer[ISO7816.OFFSET_P1] == BigNumber.FORMAT_BCD) {
            accountBalance.toBytes(buffer, (short) 0, (short) 8, BigNumber.FORMAT_BCD);
        } else if (buffer[ISO7816.OFFSET_P1] == BigNumber.FORMAT_HEX) {
            accountBalance.toBytes(buffer, (short) 0, (short) 8, BigNumber.FORMAT_HEX);
        } else {
            ISOException.throwIt(INVAILD_NUMBER_FORMAT);
        }
        return (short) 8;
    }
    
    /**
     * get purchase histories
     */
    private short getPurchaseHistories(byte[] buffer) {
        try {
            return purchaseHistories.toBytes(buffer, (short) 0);
        } catch (TLVException e) {
            if (e.getReason() == TLVException.EMPTY_TLV) {
                ISOException.throwIt(SW_PURCHASE_INFO_NOT_FOUND);
            }
        }
        return 0;
    }
    
    /**
     * update account balance
     */
    void updateAccountBalance(byte[] buffer, short amountTLVOffset, short priceTLVOffset) {
        
        short amountValueOffset = PrimitiveBERTLV.getValueOffset(buffer, amountTLVOffset);
        short priceValueOffset = PrimitiveBERTLV.getValueOffset(buffer, priceTLVOffset);
        
        // tempBigNum = amount
        tempBigNum.init(buffer, amountValueOffset, (short) 4, BigNumber.FORMAT_HEX);
        
        // tempBigNum = amount * price
        tempBigNum.multiply(buffer, priceValueOffset, (short) 4, BigNumber.FORMAT_HEX);
        
        // if account balance not enough, set account balance to 0 and throw exception
        if (accountBalance.compareTo(tempBigNum) < (byte) 0) {
            accountBalance.reset();
            ISOException.throwIt(SW_NOT_ENOUGH_ACCOUNT_BALANCE);
        }
        
        // write temBigNum to scratchSpace array
        tempBigNum.toBytes(scratchSpace, (short) 0, (short) 8, BigNumber.FORMAT_HEX);
        
        // update the accountBalance: accountBalance -= tempBigNum
        accountBalance.subtract(scratchSpace, (short) 0, (short) 8, BigNumber.FORMAT_HEX);
        
    }
    
    /**
     * verify the station signature
     * @param buffer: buffer contain signature
     * @param TVLOffset: offset to the Signature TLV
     */
    void verifyStationSignature(byte[] buffer, short TVLOffset) {
        // write signatureTag to scratchSpace array
        signatureTag.toBytes(scratchSpace, (short) 0);
        
        // get the signature TLV Offset
        short signatureTLVOffset = ConstructedBERTLV.find(buffer, TVLOffset, scratchSpace, (short) 0);
        
        // get the value of signature TLV Offset
        short signatureValueOffset = PrimitiveBERTLV.getValueOffset(buffer, signatureTLVOffset);
        
        // compare with dummySignature
        if (Util.arrayCompare(buffer, signatureValueOffset, dummySignature, (short) 0, (byte) dummySignature.length) != 0) {
            ISOException.throwIt(INVALID_STATION_SIGNATURE);
        }
        
    }
    
    /**
     * create new purchase info TLV to update purchase histories
     * @param buffer
     * @param stationIDOffset
     * @param buyTimeOffset
     * @param amountOffset
     * @param priceOffset 
     */
    private ConstructedBERTLV createNewPurchaseInfoTLV(byte buffer[], short stationIDOffset, short buyTimeOffset, short amountOffset, short priceOffset) {
        // create new empty purchaseInfo
        Util.arrayFillNonAtomic(scratchSpace, (short) 0, (short) scratchSpace.length, (byte) 0);
        purchaseInfoTag.toBytes(scratchSpace, (short) 0);
        ConstructedBERTLV purchaseInfo = (ConstructedBERTLV) BERTLV.getInstance(scratchSpace, (short) 0, (short) 2);
        
        // create the stationID TLV
        PrimitiveBERTLV stationIDTLV = (PrimitiveBERTLV) BERTLV.getInstance(buffer, stationIDOffset, (short) 7);
        
        // append to the purchaseInfo
        purchaseInfo.append(stationIDTLV);
        
        // create the buyTime TLV
        PrimitiveBERTLV buyTimeTLV = (PrimitiveBERTLV) BERTLV.getInstance(buffer, buyTimeOffset, (short) 12);
        
        // append to the purchaseInfo
        purchaseInfo.append(buyTimeTLV);
        
        // create the amount TLV
        PrimitiveBERTLV amountTLV = (PrimitiveBERTLV) BERTLV.getInstance(buffer, amountOffset, (short) 6);
        
        // append to the purchaseInfo
        purchaseInfo.append(amountTLV);
        
        // create the price TLV
        PrimitiveBERTLV priceTLV = (PrimitiveBERTLV) BERTLV.getInstance(buffer, priceOffset, (short) 6);
        purchaseInfo.append(priceTLV);
        
        // return the purchaseInfo
        return purchaseInfo;
        
    }
    
    
}

